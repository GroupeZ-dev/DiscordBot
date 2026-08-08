package fr.maxlego08.zsupport.mib;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.maxlego08.zsupport.utils.Constant;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Client HTTPS des quatre routes de réconciliation de MIB (docs/discord-tier-sync.md §4).
 *
 * <p>C'est le <b>seul</b> endroit du bot qui connaît le couple (compte Discord, palier). Le fil
 * WebSocket ne transporte qu'un jeton opaque : le palier est relu ici, au moment du traitement, ce
 * qui rend structurellement impossible l'application d'un palier périmé par un message livré en
 * retard ou en désordre.</p>
 *
 * <p>Le parsing se fait par <b>arbre Gson</b> et jamais par {@code Map<String, Object>} : c'est ce
 * dernier patron qui a produit le {@code NullPointerException} sur le champ {@code power} quand MIB
 * l'a retiré de sa réponse, en transformant une clé absente en {@code null} déréférencé au lieu d'un
 * cas géré.</p>
 *
 * <p><b>Aucune méthode ne dégrade en réponse vide.</b> Tout statut inattendu, tout corps illisible et
 * toute pagination incomplète lèvent : traiter une liste tronquée comme complète révoquerait tous les
 * ayants droit des pages manquantes.</p>
 *
 * @author Maxence
 */
public class MibApiClient {

    /**
     * Base utilisée quand la configuration n'en fournit aucune. Une base vide produirait des URI
     * relatives et une pile d'exceptions illisible au premier appel.
     */
    public static final String DEFAULT_BASE_URL = "https://minecraft-inventory-builder.com";

    private static final int BATCH_SIZE = 500;
    private static final int PAGE_LIMIT = 1000;
    private static final int MAX_PAGES = 200;
    private static final int MAX_RATE_LIMIT_WAITS = 3;
    private static final int MAX_BODY_LOG_LENGTH = 300;

    private static final Pattern DISCORD_ID_PATTERN = Pattern.compile("^\\d{15,25}$");

    /**
     * Un identifiant réellement utilisable par JDA.
     *
     * <p>Le seul motif ne suffit pas : {@code ^\d{15,25}$} accepte 21 à 25 chiffres, que
     * {@code Long.parseUnsignedLong} — utilisé par {@code Guild#getMemberById(String)} — rejette.
     * On exige donc les deux.</p>
     */
    private static boolean isUsableSnowflake(String value) {
        if (value == null || !DISCORD_ID_PATTERN.matcher(value).matches()) return false;
        try {
            Long.parseUnsignedLong(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    /**
     * Client HTTP partagé par tout le package (patron de {@code ai/AIManager}).
     *
     * <p>Un {@link HttpClient} par appel, comme le fait encore {@code VerifyManager} avec
     * {@code HttpsURLConnection}, rouvre une session TLS complète à chaque requête et fuit un pool de
     * sélecteurs depuis Java 21 où {@link HttpClient} est {@code AutoCloseable}.</p>
     *
     * <p>Les redirections sont volontairement <b>refusées</b> (valeur par défaut {@code NEVER}) : le
     * JDK ne retire que l'en-tête {@code Authorization} en suivant une redirection, il rejouerait donc
     * {@code X-Bot-Secret} vers l'hôte cible. Une base mal configurée doit échouer bruyamment, pas
     * divulguer l'oracle « ce compte Discord a-t-il un palier ».</p>
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * Gson dédié : l'instance globale de {@code ZSupport} active le pretty-printing et
     * {@code serializeNulls}, et n'est pas garantie initialisée au moment où le manager se configure.
     */
    private static final Gson GSON = new Gson();

    private final String baseUrl;
    private final String apiSecret;

    /**
     * @param baseUrl   base de l'API MIB, {@link #DEFAULT_BASE_URL} si nulle ou vide
     * @param apiSecret valeur de l'en-tête {@code X-Bot-Secret}
     */
    public MibApiClient(String baseUrl, String apiSecret) {
        String url = baseUrl == null ? "" : baseUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        this.baseUrl = url.isEmpty() ? DEFAULT_BASE_URL : url;
        this.apiSecret = apiSecret == null ? "" : apiSecret;
    }

    /**
     * Le WebSocket du relais réutilise ce client : chaque {@link HttpClient} démarre son propre pool
     * de sélecteurs, en créer un second doublerait les threads sans rien apporter.
     */
    static HttpClient sharedHttpClient() {
        return HTTP_CLIENT;
    }

    /**
     * Formate une cause racine pour les journaux, sans déverser une pile entière dans la console.
     */
    static String rootMessage(Throwable throwable) {
        if (throwable == null) return "cause inconnue";
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return current.getClass().getSimpleName() + (message == null ? "" : " : " + message);
    }

    /**
     * Tronque un corps de réponse pour le journaliser sans noyer la console.
     */
    static String truncate(String body) {
        if (body == null) return "<vide>";
        String cleaned = body.replace('\n', ' ').replace('\r', ' ').trim();
        if (cleaned.length() <= MAX_BODY_LOG_LENGTH) return cleaned;
        return cleaned.substring(0, MAX_BODY_LOG_LENGTH) + "...";
    }

    /**
     * @return vrai si un secret d'API est renseigné ; sans lui MIB répond 401 de façon systématique
     */
    public boolean isUsable() {
        return !this.apiSecret.isEmpty();
    }

    /**
     * @return la base réellement utilisée, après normalisation
     */
    public String getBaseUrl() {
        return this.baseUrl;
    }

    /**
     * Résout un <i>nudge</i> reçu sur le relais (§4.1).
     *
     * <p>Un {@code 404 unknown_ref} n'est <b>pas</b> une erreur : le jeton est à usage unique et expire
     * en 10 minutes, il signifie simplement « périmé ou déjà consommé ». On journalise et on laisse la
     * réconciliation faire son travail.</p>
     *
     * @param ref le jeton opaque reçu sur le fil
     * @return le couple (compte Discord, palier), ou {@code null} si le jeton n'est plus résolvable
     * @throws IOException          statut inattendu ou corps illisible
     * @throws InterruptedException si le thread appelant est interrompu
     */
    public MibNudge resolveRef(String ref) throws IOException, InterruptedException {
        if (ref == null || ref.isBlank()) return null;

        JsonObject payload = new JsonObject();
        payload.addProperty("ref", ref);

        HttpResponse<String> response = send(postJson("/api/v2/discord/tiers/resolve", payload), "resolve");
        if (response.statusCode() == 404) {
            System.out.println("[MIB] Nudge non résolu (404, périmé ou déjà consommé) : " + truncate(response.body()));
            return null;
        }

        JsonObject object = requireOk(response, "resolve");
        String discordId = requireString(object, "discord_id", "resolve");
        return new MibNudge(discordId, MibTier.fromKey(optString(object, "tier")));
    }

    /**
     * Résout le palier réel d'un lot d'identifiants (§4.2). C'est la forme qui produit les
     * <b>révocations</b> : elle seule répond à « ce porteur doit-il garder son rôle ? ».
     *
     * <p>Les identifiants sont dédupliqués et découpés en lots de {@value #BATCH_SIZE}, borne imposée
     * par la validation Laravel. Un identifiant hors format est écarté localement : la validation du
     * lot est globale, un seul intrus ferait échouer les 499 autres.</p>
     *
     * @param discordIds les snowflakes à résoudre, manipulés en {@code String} (64 bits)
     * @return le palier de chaque identifiant demandé
     * @throws IOException          statut inattendu ou corps illisible
     * @throws InterruptedException si le thread appelant est interrompu
     */
    public Map<String, MibTier> fetchTiers(Collection<String> discordIds) throws IOException, InterruptedException {
        Map<String, MibTier> tiers = new LinkedHashMap<>();
        if (discordIds == null || discordIds.isEmpty()) return tiers;

        Set<String> unique = new LinkedHashSet<>();
        for (String discordId : discordIds) {
            if (discordId == null) continue;
            String trimmed = discordId.trim();
            if (!DISCORD_ID_PATTERN.matcher(trimmed).matches()) {
                System.out.println("[MIB] Identifiant Discord écarté du lot (format inattendu) : " + truncate(trimmed));
                continue;
            }
            unique.add(trimmed);
        }

        List<String> batch = new ArrayList<>(BATCH_SIZE);
        for (String discordId : unique) {
            batch.add(discordId);
            if (batch.size() == BATCH_SIZE) {
                fetchTiersBatch(batch, tiers);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) fetchTiersBatch(batch, tiers);

        return tiers;
    }

    private void fetchTiersBatch(List<String> batch, Map<String, MibTier> target) throws IOException, InterruptedException {
        JsonArray ids = new JsonArray();
        for (String discordId : batch) {
            ids.add(discordId);
        }
        JsonObject payload = new JsonObject();
        payload.add("discord_ids", ids);

        String description = "tiers (lot de " + batch.size() + ")";
        JsonObject object = requireOk(send(postJson("/api/v2/discord/tiers", payload), description), description);

        JsonElement element = object.get("tiers");
        if (element == null || !element.isJsonObject()) {
            throw new IOException("Réponse MIB sans objet 'tiers' sur " + description);
        }
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            target.put(entry.getKey(), MibTier.fromKey(asString(entry.getValue())));
        }
    }

    /**
     * Parcourt l'instantané paginé des ayants droit (§4.3). C'est la forme qui produit les
     * <b>octrois</b> : elle seule découvre un compte lié pendant que le bot était hors ligne, qui ne
     * porte donc encore aucun rôle.
     *
     * <p>La boucle est plafonnée à {@value #MAX_PAGES} pages. Si le curseur est encore non nul au
     * plafond, on <b>lève</b> : un instantané tronqué traité comme complet ferait révoquer tous les
     * ayants droit des pages manquantes.</p>
     *
     * @return le palier de chaque ayant droit (premium ou ultimate uniquement)
     * @throws IOException          pagination incomplète, statut inattendu ou corps illisible
     * @throws InterruptedException si le thread appelant est interrompu
     */
    public Map<String, MibTier> fetchAllEntitled() throws IOException, InterruptedException {
        Map<String, MibTier> entitled = new LinkedHashMap<>();
        String cursor = "0";
        int pages = 0;

        while (cursor != null) {
            if (pages >= MAX_PAGES) {
                throw new IOException("Instantané MIB toujours pas terminé après " + MAX_PAGES
                        + " pages (curseur " + cursor + "), passe annulée");
            }
            pages++;

            String path = "/api/v2/discord/tiers?cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8)
                    + "&limit=" + PAGE_LIMIT;
            String description = "snapshot page " + pages;
            JsonObject object = requireOk(send(get(path), description), description);

            JsonElement items = object.get("items");
            if (items == null || !items.isJsonArray()) {
                throw new IOException("Réponse MIB sans tableau 'items' sur " + description);
            }
            for (JsonElement item : items.getAsJsonArray()) {
                if (!item.isJsonObject()) {
                    throw new IOException("Entrée d'instantané illisible sur " + description);
                }
                JsonObject entry = item.getAsJsonObject();
                String discordId = requireString(entry, "discord_id", description);

                // `discord_users.discord_id` est un VARCHAR sans contrainte de format : une seule
                // valeur non exploitable (donnée héritée, saisie manuelle en base) suffisait à faire
                // lever `guild.getMemberById(String)` — donc à ANNULER la passe entière, à chaque
                // fois, définitivement. On écarte la ligne et on continue, comme le fait déjà
                // fetchTiers() pour la même raison.
                if (!isUsableSnowflake(discordId)) {
                    System.out.println("[MIB] Ayant droit écarté de l'instantané (identifiant inexploitable) : "
                            + truncate(discordId));
                    continue;
                }

                entitled.put(discordId, MibTier.fromKey(optString(entry, "tier")));
            }

            String next = optString(object, "next_cursor");
            if (next != null && next.equals(cursor)) {
                throw new IOException("Curseur MIB qui n'avance pas (" + cursor + "), passe annulée");
            }
            cursor = next;
        }

        return entitled;
    }

    /**
     * Résolution unitaire (§4.4), utilisée par le rattrapage à l'arrivée d'un membre, la commande de
     * synchronisation manuelle et le flux ticket zMenu.
     *
     * <p>Quand MIB omet {@code can_open}, la valeur est <b>dérivée du palier</b> plutôt que laissée à
     * {@code false} : la branche de refus du flux ticket supprime le salon au bout d'une minute, un
     * champ manquant ne doit pas détruire le ticket d'un client payant.</p>
     *
     * @param discordId le snowflake, en {@code String}
     * @return le palier et le droit d'ouverture
     * @throws IOException          identifiant invalide, statut inattendu ou corps illisible
     * @throws InterruptedException si le thread appelant est interrompu
     */
    public MibSingle fetchSingle(String discordId) throws IOException, InterruptedException {
        String trimmed = discordId == null ? "" : discordId.trim();
        if (!DISCORD_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IOException("Identifiant Discord invalide : " + truncate(discordId));
        }

        String path = "/api/v2/discord/tiers/" + URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
        String description = "single " + trimmed;
        JsonObject object = requireOk(send(get(path), description), description);

        MibTier tier = MibTier.fromKey(optString(object, "tier"));
        JsonElement canOpen = object.get("can_open");
        boolean open = canOpen != null && canOpen.isJsonPrimitive()
                ? canOpen.getAsBoolean()
                : tier.isEntitled();

        return new MibSingle(tier, open);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(this.baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("X-Bot-Secret", this.apiSecret)
                .header("Accept", "application/json")
                .header("User-Agent", "zSupport/" + Constant.VERSION);
    }

    private HttpRequest.Builder get(String path) {
        return request(path).GET();
    }

    private HttpRequest.Builder postJson(String path, JsonObject payload) {
        return request(path)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8));
    }

    /**
     * Envoie la requête en respectant un éventuel {@code Retry-After}.
     *
     * <p>Le {@link HttpRequest} est reconstruit à l'identique à chaque tentative : sur 429 on
     * <b>reprend au même curseur</b>, on ne repart pas de la page 0 et on n'annule pas la passe. Au
     * delà de {@value #MAX_RATE_LIMIT_WAITS} attentes, on lève — attendre indéfiniment ferait tenir la
     * passe suivante en file derrière celle-ci.</p>
     */
    private HttpResponse<String> send(HttpRequest.Builder builder, String description) throws IOException, InterruptedException {
        int waits = 0;
        while (true) {
            HttpResponse<String> response = HTTP_CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 429) return response;

            if (waits >= MAX_RATE_LIMIT_WAITS) {
                throw new IOException("MIB répond 429 sur " + description + " après " + waits
                        + " attentes, passe abandonnée");
            }
            waits++;
            long millis = retryAfterMillis(response);
            System.out.println("[MIB] 429 sur " + description + ", nouvelle tentative dans "
                    + (millis / 1000L) + " s (" + waits + "/" + MAX_RATE_LIMIT_WAITS + ")");
            Thread.sleep(millis);
        }
    }

    private long retryAfterMillis(HttpResponse<String> response) {
        long seconds = response.headers().firstValue("Retry-After").map(value -> {
            try {
                return Long.parseLong(value.trim());
            } catch (NumberFormatException exception) {
                // L'en-tête accepte aussi une date HTTP : on ne la parse pas, on retombe sur un délai fixe.
                return 5L;
            }
        }).orElse(5L);
        return Math.min(60_000L, Math.max(1_000L, seconds * 1_000L));
    }

    private JsonObject requireOk(HttpResponse<String> response, String description) throws IOException {
        if (response.statusCode() != 200) {
            throw new IOException("MIB a répondu " + response.statusCode() + " sur " + description
                    + " : " + truncate(response.body()));
        }
        String body = response.body();
        JsonObject object;
        try {
            // Gson 2.8.5 : JsonParser.parseString n'existe pas, on passe par le TypeAdapter d'arbre.
            object = GSON.fromJson(body, JsonObject.class);
        } catch (RuntimeException exception) {
            throw new IOException("Réponse MIB illisible sur " + description + " : " + truncate(body), exception);
        }
        if (object == null) {
            throw new IOException("Réponse MIB vide sur " + description);
        }
        return object;
    }

    private String requireString(JsonObject object, String member, String description) throws IOException {
        String value = optString(object, member);
        if (value == null || value.isEmpty()) {
            throw new IOException("Champ '" + member + "' absent de la réponse MIB sur " + description);
        }
        return value;
    }

    private String optString(JsonObject object, String member) {
        return asString(object.get(member));
    }

    /**
     * Lecture défensive : une clé absente et une clé à {@code null} sont indistinguables côté JSON, et
     * c'est exactement le cas que le patron {@code Map<String, Object>} déréférençait sans le voir.
     */
    private String asString(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) return null;
        return element.getAsString();
    }

    /**
     * Résolution d'un <i>nudge</i>. Le couple (compte Discord, palier) n'existe que derrière le secret
     * d'API : il ne transite jamais sur le fil WebSocket, qui est public.
     */
    public record MibNudge(String discordId, MibTier tier) {
    }

    /**
     * Résolution unitaire, utilisée par le flux ticket zMenu.
     */
    public record MibSingle(MibTier tier, boolean canOpen) {
    }
}
