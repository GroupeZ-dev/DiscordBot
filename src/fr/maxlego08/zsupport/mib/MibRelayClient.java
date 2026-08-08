package fr.maxlego08.zsupport.mib;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Client du relais WebSocket zMenu (docs/discord-tier-sync.md §3), écrit sur {@code java.net.http}
 * pour n'ajouter <b>aucune dépendance</b> au {@code pom.xml}.
 *
 * <p>Le fil ne transporte jamais de donnée personnelle : seulement un jeton opaque à usage unique
 * ({@code discord.tier.dirty}) ou un ordre de réconciliation. Le relais ne bufferise rien et ne
 * rejoue rien — <b>la réconciliation HTTP est la source de vérité, ce socket n'est qu'une
 * optimisation de latence</b>. Aucune règle métier ne doit supposer qu'un message a été reçu.</p>
 *
 * @author Maxence
 */
public class MibRelayClient implements WebSocket.Listener {

    private static final Gson GSON = new Gson();

    private static final long BASE_BACKOFF_MILLIS = 2_000L;
    private static final long MAX_BACKOFF_MILLIS = 5L * 60L * 1_000L;
    private static final long SHORT_BACKOFF_MILLIS = 10L * 1_000L;
    private static final long FATAL_BACKOFF_MILLIS = 15L * 60L * 1_000L;
    private static final double JITTER_RATIO = 0.25D;

    /** Fermeture sans code : erreur locale du socket ou handshake qui n'a jamais abouti. */
    private static final int CODE_NONE = -1;

    private final String url;
    private final String token;
    private final ScheduledExecutorService scheduler;
    private final Consumer<String> dirtyHandler;
    private final Runnable resyncHandler;
    private final Runnable welcomeHandler;

    /**
     * Tampon d'accumulation des fragments de texte. Pas de synchronisation : le JDK garantit que les
     * appels à {@code onText} d'une même connexion sont séquentiels, et il est remis à zéro à chaque
     * ouverture puisque l'instance de listener est réutilisée d'une reconnexion à l'autre.
     */
    private final StringBuilder textBuffer = new StringBuilder();

    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean(false);

    private volatile WebSocket webSocket;
    private volatile ScheduledFuture<?> reconnectFuture;
    private volatile boolean closed;
    private volatile boolean connected;

    /**
     * @param url            l'URL {@code wss://<host>/ws} du relais
     * @param token          le jeton {@code DISCORD_BOT_RELAY_TOKEN}, envoyé dans le hello
     * @param scheduler      l'ordonnanceur dédié du manager, réutilisé pour le backoff
     * @param dirtyHandler   appelé avec le {@code ref} d'un {@code discord.tier.dirty}
     * @param resyncHandler  appelé sur {@code discord.resync}
     * @param welcomeHandler appelé quand le relais confirme le handshake
     */
    public MibRelayClient(String url, String token, ScheduledExecutorService scheduler,
                          Consumer<String> dirtyHandler, Runnable resyncHandler, Runnable welcomeHandler) {
        this.url = url;
        this.token = token;
        this.scheduler = scheduler;
        this.dirtyHandler = dirtyHandler;
        this.resyncHandler = resyncHandler;
        this.welcomeHandler = welcomeHandler;
    }

    /**
     * Ouvre la connexion. Idempotent tant que {@link #shutdown()} n'a pas été appelé.
     */
    public void start() {
        this.closed = false;
        connect();
    }

    /**
     * Ferme proprement la connexion et annule toute reconnexion en attente.
     */
    public void shutdown() {
        this.closed = true;
        this.connected = false;

        ScheduledFuture<?> future = this.reconnectFuture;
        if (future != null) future.cancel(false);

        WebSocket current = this.webSocket;
        this.webSocket = null;
        if (current != null) {
            current.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown").exceptionally(throwable -> null);
        }
    }

    /**
     * @return vrai si le relais a confirmé le handshake et n'a pas refermé depuis
     */
    public boolean isConnected() {
        return this.connected;
    }

    private void connect() {
        if (this.closed) return;

        this.textBuffer.setLength(0);
        System.out.println("[MIB] Connexion au relais " + this.url);
        try {
            MibApiClient.sharedHttpClient().newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(this.url), this)
                    .whenComplete((webSocket, throwable) -> {
                        // Un handshake raté termine ce future exceptionnellement SANS passer par
                        // onError : sans ce branchement, la reconnexion ne serait jamais programmée.
                        if (throwable != null) {
                            System.out.println("[MIB] Connexion au relais impossible : " + MibApiClient.rootMessage(throwable));
                            scheduleReconnect(CODE_NONE);
                        }
                    });
        } catch (Exception exception) {
            System.out.println("[MIB] URL de relais inutilisable (" + this.url + ") : " + MibApiClient.rootMessage(exception));
            scheduleReconnect(CODE_NONE);
        }
    }

    @Override
    public void onOpen(WebSocket webSocket) {
        // PIÈGE JDK : surcharger onOpen remplace l'implémentation par défaut, seul endroit où
        // request(1) est appelé au démarrage. Sans lui, AUCUNE trame n'est jamais livrée — pas même un
        // Ping — et le relais finit par fermer sur idle sans qu'aucune erreur ne soit visible.
        webSocket.request(1);

        this.webSocket = webSocket;
        this.textBuffer.setLength(0);
        System.out.println("[MIB] Socket relais ouvert, envoi du hello");

        JsonObject hello = new JsonObject();
        hello.addProperty("type", "hello");
        hello.addProperty("token", this.token);
        webSocket.sendText(GSON.toJson(hello), true).exceptionally(throwable -> {
            System.out.println("[MIB] Envoi du hello impossible : " + MibApiClient.rootMessage(throwable));
            return null;
        });
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        try {
            // PIÈGE JDK : un message peut arriver en plusieurs fragments. Parser avant last == true
            // découperait le JSON au milieu et transformerait chaque gros message en erreur.
            this.textBuffer.append(data);
            if (last) {
                String message = this.textBuffer.toString();
                this.textBuffer.setLength(0);
                handleMessage(message);
            }
        } catch (Throwable throwable) {
            // Une exception qui s'échappe d'onText part dans onError et TUE la connexion : un message
            // malformé ne doit jamais coûter le socket.
            this.textBuffer.setLength(0);
            System.out.println("[MIB] Message relais ignoré (traitement en erreur) : " + MibApiClient.rootMessage(throwable));
        } finally {
            webSocket.request(1);
        }
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        this.webSocket = null;
        this.connected = false;

        System.out.println("[MIB] Relais fermé : code=" + statusCode
                + " raison=" + (reason == null || reason.isEmpty() ? "<aucune>" : reason));
        scheduleReconnect(statusCode);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        this.webSocket = null;
        this.connected = false;
        System.out.println("[MIB] Erreur du socket relais : " + MibApiClient.rootMessage(error));
        scheduleReconnect(CODE_NONE);
    }

    private void handleMessage(String raw) {
        JsonObject object = GSON.fromJson(raw, JsonObject.class);
        if (object == null) {
            System.out.println("[MIB] Message relais vide, ignoré");
            return;
        }

        String type = asString(object.get("type"));
        if (type == null) {
            System.out.println("[MIB] Message relais sans champ 'type', ignoré : " + MibApiClient.truncate(raw));
            return;
        }

        switch (type) {
            case "welcome" -> {
                this.connected = true;
                this.attempts.set(0);
                System.out.println("[MIB] Relais connecté (connection_id=" + asString(object.get("connection_id")) + ")");
                if (this.welcomeHandler != null) this.welcomeHandler.run();
            }
            case "discord.tier.dirty" -> {
                String ref = asString(object.get("ref"));
                if (ref == null || ref.isBlank()) {
                    System.out.println("[MIB] Nudge sans 'ref', ignoré : " + MibApiClient.truncate(raw));
                    return;
                }
                if (this.dirtyHandler != null) this.dirtyHandler.accept(ref);
            }
            case "discord.resync" -> {
                if (this.resyncHandler != null) this.resyncHandler.run();
            }
            // Jamais d'ignorance silencieuse : c'est ainsi qu'une divergence de contrat devient invisible.
            default -> System.out.println("[MIB] Type de message relais inconnu, ignoré : " + type + " -> " + MibApiClient.truncate(raw));
        }
    }

    private String asString(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        return element.getAsString();
    }

    /**
     * Programme une reconnexion selon le code de fermeture (contrat §3.5).
     *
     * <p>Le backoff exponentiel 2 s → 5 min avec jitter d'au plus 25 % est une exigence de sécurité,
     * pas un confort : la route d'introspection du relais est throttlée à 60/min sur son IP, partagée
     * avec les serveurs Minecraft — un bot qui reconnecte en boucle les met tous en 429.</p>
     *
     * <p>Le compteur de tentatives court dans <b>tous</b> les cas et n'est remis à zéro que par un
     * {@code welcome}. C'est ce qui permet à la reconnexion immédiate du code 4008 de retomber
     * d'elle-même dans le backoff normal si elle se répète.</p>
     */
    private void scheduleReconnect(int statusCode) {
        if (this.closed) return;
        // onClose et un future de handshake en échec peuvent tous deux demander une reconnexion :
        // le CAS garantit qu'on n'en programme qu'une seule.
        if (!this.reconnectScheduled.compareAndSet(false, true)) return;

        int attempt = this.attempts.incrementAndGet();
        long delay = switch (statusCode) {
            // 4002 (jeton absent), 4004 (non autorisé) et 4009 (remplacé par une autre instance) ne se
            // règlent pas en reconnectant : ils demandent une intervention humaine.
            case 4002, 4004, 4009 -> FATAL_BACKOFF_MILLIS;
            // 4008 : fermeture sur inactivité, bénigne et attendue. On repart tout de suite, mais
            // seulement si la session précédente avait abouti — sinon on escalade.
            case 4008 -> attempt <= 1 ? 0L : jitter(backoff(attempt));
            // 1001 : arrêt planifié du relais, il revient vite mais pas instantanément.
            case 1001 -> jitter(Math.min(SHORT_BACKOFF_MILLIS, backoff(attempt)));
            default -> jitter(backoff(attempt));
        };

        System.out.println("[MIB] Reconnexion au relais dans " + (delay / 1000L) + " s (tentative " + attempt
                + (delay == FATAL_BACKOFF_MILLIS ? ", palier FATAL" : "") + ")");
        try {
            this.reconnectFuture = this.scheduler.schedule(() -> {
                this.reconnectScheduled.set(false);
                try {
                    connect();
                } catch (Throwable throwable) {
                    System.out.println("[MIB] Reconnexion en erreur : " + MibApiClient.rootMessage(throwable));
                    scheduleReconnect(CODE_NONE);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException exception) {
            // Ordonnanceur arrêté : le bot s'éteint, il n'y a plus rien à reconnecter.
            this.reconnectScheduled.set(false);
        }
    }

    private long backoff(int attempt) {
        return Math.min(MAX_BACKOFF_MILLIS, BASE_BACKOFF_MILLIS * (1L << Math.min(attempt - 1, 30)));
    }

    private long jitter(long base) {
        return base - (long) (base * JITTER_RATIO * ThreadLocalRandom.current().nextDouble());
    }
}
