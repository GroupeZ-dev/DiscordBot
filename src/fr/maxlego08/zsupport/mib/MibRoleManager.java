package fr.maxlego08.zsupport.mib;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.utils.Plugin;
import fr.maxlego08.zsupport.utils.ZUtils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Applique sur Discord le palier MIB des membres : pose zMenuPremium ou zMenuPro, retire l'autre,
 * retire les deux quand le palier retombe en {@code free} (docs/discord-tier-sync.md §7).
 *
 * <p><b>MIB devient la source de vérité exclusive de ces deux rôles.</b> La réconciliation retirera
 * tout octroi manuel : aucune trace d'origine n'existe côté Discord.</p>
 *
 * <p>Le manager est piloté par une {@link Settings} injectée par le lot de câblage. Il ne lit
 * volontairement <b>aucune</b> nouvelle clé de {@code Config} : ce découplage lui permet de compiler
 * et d'être relu indépendamment de l'ajout des clés de configuration.</p>
 *
 * @author Maxence
 */
public class MibRoleManager extends ZUtils {

    private static final String PREFIX = "[MIB] ";

    private static final int DEFAULT_MAX_REVOCATIONS = 200;
    private static final int DEFAULT_RECONCILE_MINUTES = 60;
    private static final long INITIAL_RECONCILE_DELAY_MINUTES = 2L;
    private static final long DEBOUNCE_MILLIS = 60_000L;

    /**
     * Nombre de changements appliqués par seconde. Discord rate-limite agressivement les mutations de
     * rôles : une passe de première mise en service (des centaines d'octrois) envoyée d'un bloc ferait
     * mettre le bot en attente globale par JDA, tickets compris.
     */
    private static final int APPLY_PER_SECOND = 4;

    private static final int MAX_APPLY_ATTEMPTS = 3;
    private static final long RETRY_DELAY_SECONDS = 30L;

    private static volatile MibRoleManager instance;

    /**
     * Ordonnanceur <b>dédié</b>, mono-thread.
     *
     * <p>Ni {@code SqlManager.service} (pool fixe de 4 threads partagé avec les tickets et la FAQ) ni
     * {@code TicketManager.scheduler} ne sont utilisés : une passe de réconciliation bloque son thread
     * sur des appels HTTPS de plusieurs secondes, ce qui gèlerait l'ouverture des tickets.</p>
     */
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mib-tier-sync");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Ordonnanceur du chemin <b>interactif</b> et du transport, séparé de celui du travail de masse.
     *
     * <p>Sans cette séparation, la vérification d'un ticket zMenu était sérialisée derrière la
     * réconciliation : celle-ci bloque son thread sur des appels HTTPS (15 s de délai chacun, plus
     * jusqu'à 3 × 60 s d'attente sur un 429), pendant lesquels le client voyait l'embed « Check your
     * purchase, please wait. » figé sans aucun repli. Le backoff de reconnexion du relais était pris
     * dans la même file : une reconnexion programmée à +2 s pouvait partir plusieurs minutes plus
     * tard.</p>
     *
     * <p>Deux threads suffisent : on n'y poste que des requêtes unitaires et des reconnexions.</p>
     */
    private final ScheduledExecutorService interactive = Executors.newScheduledThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "mib-tier-io");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean reconcileScheduled = new AtomicBoolean(false);
    private final AtomicBoolean missingLogged = new AtomicBoolean(false);

    private volatile Settings settings = new Settings();
    private volatile MibApiClient apiClient = new MibApiClient(null, "");
    private volatile MibRelayClient relayClient;
    private volatile boolean started;
    private volatile long lastReconcileAt;

    private MibRoleManager() {
    }

    /**
     * Return a singleton instance of MibRoleManager.
     */
    public static MibRoleManager getInstance() {
        // Double lock for thread safety.
        if (instance == null) {
            synchronized (MibRoleManager.class) {
                if (instance == null) {
                    instance = new MibRoleManager();
                }
            }
        }
        return instance;
    }

    /**
     * Injecte la configuration. À appeler <b>avant</b> {@link #start()}.
     */
    public void configure(Settings settings) {
        this.settings = settings == null ? new Settings() : settings;
        this.apiClient = new MibApiClient(this.settings.apiBaseUrl, this.settings.apiSecret);
        this.missingLogged.set(false);
    }

    public Settings getSettings() {
        return this.settings;
    }

    /**
     * @return le client HTTPS, réutilisable par le flux ticket zMenu
     */
    public MibApiClient getApiClient() {
        return this.apiClient;
    }

    public boolean isStarted() {
        return this.started;
    }

    public boolean isRelayConnected() {
        MibRelayClient relay = this.relayClient;
        return relay != null && relay.isConnected();
    }

    /**
     * Vérifie que la synchronisation dispose de tout ce dont elle a besoin.
     *
     * <p>Le test {@code premiumRoleId != 0 && proRoleId != 0} n'est pas cosmétique :
     * {@code ZUtils.hasRole(member, 0)} renvoie <b>true pour tout le monde</b>, un identifiant à zéro
     * inverserait donc silencieusement toute la détection d'idempotence.</p>
     *
     * <p>Secrets vides ⇒ synchronisation désactivée (no-op sûr) plutôt qu'une boucle de 4002 et de
     * 401 contre un relais et une API qui refuseront de toute façon.</p>
     *
     * @return vrai si la synchronisation peut démarrer ; sinon journalise <b>une seule fois</b> les
     * clés manquantes
     */
    public boolean isConfigured() {
        Settings settings = this.settings;
        List<String> missing = new ArrayList<>();

        if (settings == null) {
            logMissing(List.of("configuration absente"));
            return false;
        }
        if (!settings.enabled) missing.add("enabled");
        if (settings.premiumRoleId == 0L) missing.add("premiumRoleId");
        if (settings.proRoleId == 0L) missing.add("proRoleId");
        if (isBlank(settings.wsUrl)) missing.add("wsUrl");
        if (isBlank(settings.relayToken)) missing.add("relayToken");
        if (isBlank(settings.apiSecret)) missing.add("apiSecret");

        if (missing.isEmpty()) return true;
        logMissing(missing);
        return false;
    }

    /**
     * Démarre le relais et la réconciliation périodique.
     *
     * <p>Deux garde-fous bloquants avant tout démarrage : la configuration doit être complète, et
     * <b>aucun des deux rôles ne doit figurer dans {@code Config.plugins}</b>. Dans ce dernier cas
     * {@code RoleManager} les mémoriserait dans {@code roles.json} au premier
     * {@code GuildMemberRoleAddEvent} et {@code giveRoles} les ré-attribuerait à chaque retour du
     * membre sur le serveur, annulant définitivement toute révocation. Le {@code config.json} de
     * production remplaçant intégralement la liste des plugins, la vérification doit être faite au
     * runtime et non à la lecture du code.</p>
     */
    public synchronized void start() {
        if (this.started) return;
        if (!isConfigured()) return;

        Settings settings = this.settings;

        Optional<Plugin> conflict = Config.plugins.stream().filter(plugin -> plugin.getRole() == settings.premiumRoleId || plugin.getRole() == settings.proRoleId).findFirst();
        if (conflict.isPresent()) {
            System.err.println(PREFIX + "SEVERE : le rôle de palier est déclaré dans Config.plugins (plugin « " + conflict.get().getName() + " »). RoleManager le mémoriserait dans roles.json et giveRoles le " + "ré-attribuerait à chaque retour du membre, ce qui annulerait définitivement toute révocation. " + "Synchronisation MIB REFUSÉE.");
            return;
        }

        this.started = true;

        // Le relais reçoit l'ordonnanceur INTERACTIF : son backoff de reconnexion (2 s au premier
        // essai) doit partir à l'heure, pas derrière une passe de réconciliation en cours.
        this.relayClient = new MibRelayClient(settings.wsUrl, settings.relayToken, this.interactive, this::syncByRef, () -> reconcileAsync(null), () -> {
            // La passe post-welcome est justement celle qui répare les nudges perdus pendant la
            // coupure : le relais ne bufferise rien et ne rejoue rien.
            log("Relais connecté, réconciliation de rattrapage demandée.");
            reconcileAsync(null);
        });
        this.relayClient.start();

        int interval = settings.reconcileIntervalMinutes > 0 ? settings.reconcileIntervalMinutes : DEFAULT_RECONCILE_MINUTES;
        this.scheduler.scheduleAtFixedRate(() -> {
            // Une exception qui s'échappe d'une tâche scheduleAtFixedRate l'ANNULE définitivement et
            // silencieusement : la réconciliation ne repartirait qu'au prochain redémarrage du bot.
            try {
                reconcileAsync(null);
            } catch (Throwable throwable) {
                logError("Réconciliation périodique", throwable);
            }
        }, INITIAL_RECONCILE_DELAY_MINUTES, interval, TimeUnit.MINUTES);

        log("Synchronisation des paliers démarrée (simulation=" + settings.dryRun + ", intervalle=" + interval + " min, plafond de révocations=" + maxRevocations() + ").");
    }

    /**
     * Ferme le relais et arrête l'ordonnanceur dédié.
     */
    public synchronized void shutdown() {
        this.started = false;

        MibRelayClient relay = this.relayClient;
        if (relay != null) relay.shutdown();

        stopExecutor(this.scheduler);
        stopExecutor(this.interactive);
    }

    /**
     * Arrêt propre borné à 5 s, puis interruption.
     */
    private void stopExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5L, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Résout un <i>nudge</i> reçu sur le relais puis applique le palier.
     *
     * <p>Le palier est relu au moment du traitement : un message livré en retard ou en désordre ne
     * peut donc pas appliquer un palier périmé, et aucun champ d'ordonnancement n'est nécessaire.</p>
     */
    public void syncByRef(String ref) {
        if (!this.started) return;
        submit("nudge", () -> {
            MibApiClient.MibNudge nudge = this.apiClient.resolveRef(ref);
            if (nudge == null) return;

            Guild guild = guild();
            if (guild == null) return;

            applyToMember(guild, nudge.discordId(), nudge.tier(), null);
        });
    }

    /**
     * Rattrapage unitaire, pour {@code GuildMemberJoinEvent} et la commande de synchronisation.
     */
    public void syncMemberAsync(long discordId) {
        syncMemberAsync(discordId, null, null);
    }

    /**
     * Rattrapage unitaire avec retour d'exécution.
     *
     * @param onResult appelé avec l'issue de l'application, peut être {@code null}
     * @param onError  appelé si MIB est injoignable ou répond de travers, peut être {@code null}
     */
    public void syncMemberAsync(long discordId, Consumer<MibApplyResult> onResult, Consumer<Throwable> onError) {
        String id = Long.toUnsignedString(discordId);
        submit("member " + id, () -> {
            MibApiClient.MibSingle single = this.apiClient.fetchSingle(id);
            Guild guild = guild();
            if (guild == null) {
                accept(onError, new IllegalStateException("guilde introuvable"));
                return;
            }
            applyToMember(guild, id, single.tier(), onResult);
        }, onError);
    }

    /**
     * Résolution unitaire asynchrone, sans application de rôle : c'est ce dont a besoin le flux ticket
     * zMenu (§7.3).
     *
     * <p>{@code onError} y est <b>indispensable</b> : il distingue une panne (timeout, 5xx, 401, corps
     * illisible) d'un refus légitime. La branche de refus supprime le salon du ticket au bout d'une
     * minute, une panne MIB doit router vers la validation humaine, jamais vers la fermeture.</p>
     */
    public void fetchSingleAsync(long discordId, Consumer<MibApiClient.MibSingle> onSuccess, Consumer<Throwable> onError) {
        MibApiClient client = this.apiClient;
        if (client == null || !client.isUsable()) {
            // Traité comme une PANNE et non comme un refus : sans secret, MIB répond 401 de façon
            // systématique et on ne sait rien du droit du client.
            accept(onError, new IllegalStateException("secret d'API MIB non renseigné"));
            return;
        }
        // Chemin INTERACTIF : posté sur `interactive`, jamais sur l'ordonnanceur de masse — un ticket
        // client ne doit pas attendre la fin d'une réconciliation complète pour obtenir sa réponse.
        submitOn(this.interactive, "single " + discordId, () -> accept(onSuccess, client.fetchSingle(Long.toUnsignedString(discordId))), onError);
    }

    /**
     * Applique un palier sur un membre, de façon <b>idempotente</b> et <b>ciblée</b>.
     *
     * @param consumer appelé une fois avec l'issue, peut être {@code null}
     */
    public void applyTier(Guild guild, Member member, MibTier tier, Consumer<MibApplyResult> consumer) {
        applyTier(guild, member, tier, 1, consumer);
    }

    private void applyTier(Guild guild, Member member, MibTier tier, int attempt, Consumer<MibApplyResult> consumer) {
        if (guild == null || member == null) {
            accept(consumer, MibApplyResult.unknownMember(tier));
            return;
        }

        Settings settings = this.settings;
        long targetRoleId = tier.roleIdFor(settings.premiumRoleId, settings.proRoleId);

        // Le delta est TOUJOURS recalculé ici, depuis les rôles du membre au moment de l'application, et
        // jamais transporté depuis l'appelant : c'est ce qui rend l'opération idempotente même quand
        // deux nudges arrivent en désordre ou qu'une passe croise un nudge.
        List<Long> toRemove = new ArrayList<>(2);
        if (settings.premiumRoleId != targetRoleId && holdsRole(member, settings.premiumRoleId)) {
            toRemove.add(settings.premiumRoleId);
        }
        if (settings.proRoleId != targetRoleId && holdsRole(member, settings.proRoleId)) {
            toRemove.add(settings.proRoleId);
        }
        List<Long> toAdd = targetRoleId != 0L && !holdsRole(member, targetRoleId) ? List.of(targetRoleId) : List.of();

        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            accept(consumer, MibApplyResult.unchanged(tier));
            return;
        }

        if (settings.dryRun) {
            log("[simulation] " + member.getUser().getName() + " (" + member.getId() + ") -> " + tier.getKey() + " | ajout " + toAdd + " retrait " + toRemove);
            accept(consumer, MibApplyResult.simulated(tier, toAdd, toRemove));
            return;
        }

        if (toAdd.isEmpty()) {
            applyRemovals(guild, member, tier, toAdd, toRemove, attempt, consumer);
            return;
        }

        Role role = guild.getRoleById(targetRoleId);
        if (role == null) {
            logError("Rôle " + targetRoleId + " introuvable sur la guilde", null);
            accept(consumer, MibApplyResult.failed(tier, toAdd, toRemove, "rôle " + targetRoleId + " introuvable"));
            return;
        }

        // addRoleToMember : endpoint mono-rôle. modifyMemberRoles(member, add, remove) enverrait le
        // tableau COMPLET des rôles calculé depuis le cache et écraserait toute modification
        // concurrente faite par un modérateur entre la lecture et l'écriture.
        guild.addRoleToMember(member, role).queue(success -> applyRemovals(guild, member, tier, toAdd, toRemove, attempt, consumer), throwable -> {
            if (isUnknownMember(throwable)) {
                accept(consumer, MibApplyResult.unknownMember(tier));
                return;
            }
            logError("Ajout du rôle " + targetRoleId + " à " + member.getId(), throwable);
            accept(consumer, MibApplyResult.failed(tier, toAdd, toRemove, MibApiClient.rootMessage(throwable)));
        });
    }

    private void applyRemovals(Guild guild, Member member, MibTier tier, List<Long> added, List<Long> toRemove, int attempt, Consumer<MibApplyResult> consumer) {
        if (toRemove.isEmpty()) {
            accept(consumer, MibApplyResult.applied(tier, added, toRemove));
            return;
        }

        AtomicInteger pending = new AtomicInteger(toRemove.size());
        AtomicBoolean unknown = new AtomicBoolean(false);
        AtomicBoolean failed = new AtomicBoolean(false);

        for (long roleId : toRemove) {
            Role role = guild.getRoleById(roleId);
            if (role == null) {
                logError("Rôle " + roleId + " introuvable sur la guilde", null);
                failed.set(true);
                if (pending.decrementAndGet() == 0) {
                    finishRemovals(guild, member, tier, added, toRemove, attempt, unknown, failed, consumer);
                }
                continue;
            }
            guild.removeRoleFromMember(member, role).queue(success -> {
                if (pending.decrementAndGet() == 0) {
                    finishRemovals(guild, member, tier, added, toRemove, attempt, unknown, failed, consumer);
                }
            }, throwable -> {
                if (isUnknownMember(throwable)) {
                    unknown.set(true);
                } else {
                    failed.set(true);
                    logError("Retrait du rôle " + roleId + " à " + member.getId(), throwable);
                }
                if (pending.decrementAndGet() == 0) {
                    finishRemovals(guild, member, tier, added, toRemove, attempt, unknown, failed, consumer);
                }
            });
        }
    }

    private void finishRemovals(Guild guild, Member member, MibTier tier, List<Long> added, List<Long> toRemove, int attempt, AtomicBoolean unknown, AtomicBoolean failed, Consumer<MibApplyResult> consumer) {
        if (unknown.get()) {
            accept(consumer, MibApplyResult.unknownMember(tier));
            return;
        }
        if (!failed.get()) {
            accept(consumer, MibApplyResult.applied(tier, added, toRemove));
            return;
        }

        // Un retrait raté laisse le membre avec les DEUX rôles, ce qui viole l'exclusivité du contrat.
        // On ré-empile, avec un compteur d'essais borné pour ne pas boucler sur une erreur permanente
        // (rôle au-dessus du bot dans la hiérarchie, permission manquante...).
        if (attempt < MAX_APPLY_ATTEMPTS && this.started) {
            long delay = RETRY_DELAY_SECONDS * attempt;
            log("Retrait de rôle en échec pour " + member.getId() + ", nouvel essai dans " + delay + " s (" + (attempt + 1) + "/" + MAX_APPLY_ATTEMPTS + ")");
            try {
                this.scheduler.schedule(() -> {
                    try {
                        applyTier(guild, member, tier, attempt + 1, null);
                    } catch (Throwable throwable) {
                        logError("Nouvel essai d'application", throwable);
                    }
                }, delay, TimeUnit.SECONDS);
            } catch (RejectedExecutionException ignored) {
                // Ordonnanceur arrêté : le bot s'éteint, la prochaine réconciliation rattrapera.
            }
        }
        accept(consumer, MibApplyResult.failed(tier, added, toRemove, "retrait de rôle en échec"));
    }

    /**
     * Lance une réconciliation complète (§7.2).
     *
     * <p>L'anti-rebond de 60 s <b>diffère</b> la passe, il ne la jette pas : la passe post-welcome est
     * précisément celle qui répare les nudges perdus pendant une coupure, la jeter reviendrait à ne
     * jamais converger après un incident réseau.</p>
     *
     * @param consumer appelé avec le bilan, peut être {@code null}
     */
    public void reconcileAsync(Consumer<MibSyncReport> consumer) {
        if (!this.started) {
            accept(consumer, MibSyncReport.skipped("synchronisation MIB désactivée"));
            return;
        }

        long delay = Math.max(0L, (this.lastReconcileAt + DEBOUNCE_MILLIS) - System.currentTimeMillis());
        try {
            if (delay > 0L) {
                if (!this.reconcileScheduled.compareAndSet(false, true)) {
                    accept(consumer, MibSyncReport.skipped("une passe est déjà différée"));
                    return;
                }
                log("Réconciliation différée de " + (delay / 1000L) + " s (anti-rebond).");
                this.scheduler.schedule(() -> {
                    this.reconcileScheduled.set(false);
                    runReconcile(consumer);
                }, delay, TimeUnit.MILLISECONDS);
                return;
            }
            this.scheduler.execute(() -> runReconcile(consumer));
        } catch (RejectedExecutionException exception) {
            accept(consumer, MibSyncReport.skipped("ordonnanceur arrêté"));
        }
    }

    private void runReconcile(Consumer<MibSyncReport> consumer) {
        // Positionné au DÉBUT de la passe : si on le positionnait à la fin, une passe longue laisserait
        // passer une rafale de déclencheurs juste après elle.
        this.lastReconcileAt = System.currentTimeMillis();

        MibSyncReport report;
        long startedNanos = System.nanoTime();
        try {
            report = reconcile(startedNanos);
        } catch (Throwable throwable) {
            logError("Réconciliation", throwable);
            report = MibSyncReport.failed(MibApiClient.rootMessage(throwable), startedNanos);
        }

        if (report.isSuccess()) {
            log(report.toSummary());
        } else {
            System.err.println(PREFIX + report.toSummary());
        }
        accept(consumer, report);
    }

    private MibSyncReport reconcile(long startedNanos) {
        Settings settings = this.settings;

        Guild guild = guild();
        if (guild == null) {
            return MibSyncReport.failed("guilde " + settings.guildId + " introuvable (JDA pas encore prêt ?)", startedNanos);
        }

        Role premiumRole = guild.getRoleById(settings.premiumRoleId);
        Role proRole = guild.getRoleById(settings.proRoleId);
        if (premiumRole == null || proRole == null) {
            return MibSyncReport.failed("rôles de palier introuvables sur la guilde", startedNanos);
        }

        Map<String, MibTier> expected;
        Set<String> holderIds = new LinkedHashSet<>();
        Map<String, MibTier> holderTiers;
        try {
            // 1. les ayants droit (produit les OCTROIS : un compte lié pendant que le bot était hors
            //    ligne ne porte encore aucun rôle et n'apparaît donc dans aucune autre source).
            expected = this.apiClient.fetchAllEntitled();

            // 2. les porteurs locaux, lus dans le cache JDA : zéro appel REST.
            guild.getMembersWithRoles(premiumRole).forEach(member -> holderIds.add(member.getId()));
            guild.getMembersWithRoles(proRole).forEach(member -> holderIds.add(member.getId()));

            // 3. leur palier réel (produit les RÉVOCATIONS : c'est la seule forme qui répond à « ce
            //    porteur doit-il garder son rôle ? »).
            holderTiers = this.apiClient.fetchTiers(holderIds);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return MibSyncReport.failed("passe interrompue", startedNanos);
        } catch (Exception exception) {
            // Toute erreur d'API ANNULE la passe. Jamais de dégradation en instantané partiel : traiter
            // une liste tronquée comme complète révoquerait tous les ayants droit des pages manquantes.
            return MibSyncReport.failed(MibApiClient.rootMessage(exception), startedNanos);
        }

        Map<String, MibTier> targets = new LinkedHashMap<>(expected);
        int unresolvedHolders = 0;
        for (String holderId : holderIds) {
            MibTier real = holderTiers.get(holderId);
            if (real == null) {
                // Le contrat garantit que tous les identifiants demandés figurent dans la réponse. En
                // son absence on ne révoque surtout pas : on ignore ce porteur pour cette passe.
                unresolvedHolders++;
                continue;
            }
            // Le lot est résolu APRÈS l'instantané : sa valeur est la plus fraîche des deux.
            targets.put(holderId, real);
        }

        List<Change> grants = new ArrayList<>();
        List<Change> corrections = new ArrayList<>();
        List<Change> revocations = new ArrayList<>();
        int unknownMembers = 0;

        for (Map.Entry<String, MibTier> entry : targets.entrySet()) {
            Member member;
            try {
                member = guild.getMemberById(entry.getKey());
            } catch (RuntimeException exception) {
                // Défense en profondeur : getMemberById parse l'identifiant et lève sur une valeur
                // inexploitable. Laisser remonter annulerait la passe entière pour UNE ligne.
                log("Identifiant ignoré pendant la réconciliation : " + entry.getKey());
                unknownMembers++;
                continue;
            }
            if (member == null) {
                // Pas sur le serveur : no-op, le rattrapage se fera à son arrivée.
                unknownMembers++;
                continue;
            }

            MibTier tier = entry.getValue();
            long targetRoleId = tier.roleIdFor(settings.premiumRoleId, settings.proRoleId);
            boolean hasPremium = holdsRole(member, settings.premiumRoleId);
            boolean hasPro = holdsRole(member, settings.proRoleId);
            boolean needsAdd = targetRoleId != 0L && !holdsRole(member, targetRoleId);
            boolean needsRemove = (hasPremium && settings.premiumRoleId != targetRoleId) || (hasPro && settings.proRoleId != targetRoleId);

            if (!needsAdd && !needsRemove) continue;

            Change change = new Change(member, tier);
            if (tier == MibTier.FREE) {
                // Une révocation, c'est exactement « le membre perd son statut payant ». Un membre qui
                // porte les deux rôles alors qu'il est premium est une CORRECTION : il reste ayant
                // droit, et le plafond anti-catastrophe ne doit pas l'empêcher.
                revocations.add(change);
            } else if (needsAdd && !hasPremium && !hasPro) {
                grants.add(change);
            } else {
                corrections.add(change);
            }
        }

        int maxRevocations = maxRevocations();
        int skippedRevocations = 0;
        if (revocations.size() > maxRevocations) {
            skippedRevocations = revocations.size();
            System.err.println(PREFIX + "SEVERE : " + skippedRevocations + " révocations dépassent le plafond de " + maxRevocations + ". Les octrois et corrections sont appliqués, les révocations sont " + "ABANDONNÉES. Vérifier MIB avant de relancer une passe.");
            revocations = Collections.emptyList();
        }

        List<Change> changes = new ArrayList<>(grants.size() + corrections.size() + revocations.size());
        changes.addAll(grants);
        changes.addAll(corrections);
        changes.addAll(revocations);
        scheduleApplications(guild, changes);

        String message = unresolvedHolders > 0 ? unresolvedHolders + " porteur(s) non résolu(s), laissés en l'état" : "";
        return MibSyncReport.success(settings.dryRun, expected.size(), holderIds.size(), grants.size(), corrections.size(), revocations.size(), skippedRevocations, unknownMembers, message, startedNanos);
    }

    /**
     * Étale l'application des changements à {@value #APPLY_PER_SECOND} par seconde. Les tâches ne font
     * que poser des {@code RestAction} en file JDA, elles ne bloquent pas l'ordonnanceur.
     */
    private void scheduleApplications(Guild guild, List<Change> changes) {
        int index = 0;
        for (Change change : changes) {
            long delay = (index / APPLY_PER_SECOND) * 1000L;
            index++;
            try {
                this.scheduler.schedule(() -> {
                    try {
                        applyTier(guild, change.member(), change.tier(), null);
                    } catch (Throwable throwable) {
                        logError("Application différée", throwable);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException exception) {
                return;
            }
        }
    }

    private void applyToMember(Guild guild, String discordId, MibTier tier, Consumer<MibApplyResult> consumer) {
        Member cached = guild.getMemberById(discordId);
        if (cached != null) {
            applyTier(guild, cached, tier, consumer);
            return;
        }
        guild.retrieveMemberById(discordId).queue(member -> applyTier(guild, member, tier, consumer), throwable -> {
            if (isUnknownMember(throwable)) {
                accept(consumer, MibApplyResult.unknownMember(tier));
                return;
            }
            logError("Récupération du membre " + discordId, throwable);
            accept(consumer, MibApplyResult.failed(tier, List.of(), List.of(), MibApiClient.rootMessage(throwable)));
        });
    }

    /**
     * Volontairement <b>pas</b> {@code ZUtils.hasRole(member, id)} : son prédicat contient
     * {@code || id == 0}, il renvoie donc {@code true} pour tout le monde dès que l'identifiant vaut
     * zéro, ce qui inverserait silencieusement toute la détection d'idempotence.
     */
    private boolean holdsRole(Member member, long roleId) {
        if (roleId == 0L) return false;
        return member.getRoles().stream().anyMatch(role -> role.getIdLong() == roleId);
    }

    /**
     * Un membre absent du serveur n'est pas une erreur : le rattrapage se fera à son arrivée.
     */
    private boolean isUnknownMember(Throwable throwable) {
        return throwable instanceof ErrorResponseException exception && exception.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER;
    }

    private Guild guild() {
        ZSupport support = ZSupport.instance;
        if (support == null) return null;
        JDA jda = support.getJda();
        if (jda == null) return null;
        return jda.getGuildById(this.settings.guildId);
    }

    private int maxRevocations() {
        int max = this.settings.maxRevocations;
        return max > 0 ? max : DEFAULT_MAX_REVOCATIONS;
    }

    private void submit(String description, ThrowingRunnable task) {
        submit(description, task, null);
    }

    /**
     * Exécute une tâche sur l'ordonnanceur dédié en attrapant tout {@link Throwable} : une exception
     * qui remonte jusqu'à l'ordonnanceur perdrait la tâche sans le moindre message.
     */
    private void submit(String description, ThrowingRunnable task, Consumer<Throwable> onError) {
        submitOn(this.scheduler, description, task, onError);
    }

    /**
     * Variante qui choisit explicitement l'ordonnanceur. Le chemin interactif (flux ticket,
     * synchronisation ciblée) passe par {@link #interactive} pour ne jamais attendre derrière une
     * passe de réconciliation, qui peut occuper son thread plusieurs minutes.
     */
    private void submitOn(ExecutorService executor, String description, ThrowingRunnable task, Consumer<Throwable> onError) {
        try {
            executor.execute(() -> {
                try {
                    task.run();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    accept(onError, exception);
                } catch (Throwable throwable) {
                    logError(description, throwable);
                    accept(onError, throwable);
                }
            });
        } catch (RejectedExecutionException exception) {
            accept(onError, exception);
        }
    }

    private <T> void accept(Consumer<T> consumer, T value) {
        if (consumer == null) return;
        try {
            consumer.accept(value);
        } catch (Throwable throwable) {
            logError("Callback", throwable);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void logMissing(List<String> missing) {
        // Une seule fois : sans ce garde, chaque tick de réconciliation reproduirait la même ligne.
        if (!this.missingLogged.compareAndSet(false, true)) return;
        log("Synchronisation des paliers désactivée, clé(s) manquante(s) : " + String.join(", ", missing));
    }

    private void log(String message) {
        System.out.println(PREFIX + message);
    }

    private void logError(String description, Throwable throwable) {
        System.err.println(PREFIX + description + " : " + MibApiClient.rootMessage(throwable));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    /**
     * Un changement planifié par une passe de réconciliation.
     */
    private record Change(Member member, MibTier tier) {
    }

    /**
     * Contrat de configuration entre ce lot et le lot de câblage.
     *
     * <p>Les champs sont publics et mutables à dessein : le câblage les renseigne depuis {@code Config}
     * puis appelle {@link MibRoleManager#configure(Settings)}. Ce découplage est ce qui permet à ce
     * package de compiler alors que {@code Config} ne contient pas encore les nouvelles clés.</p>
     *
     * <p>Les secrets doivent être lus <b>en priorité depuis l'environnement</b>
     * ({@code MIB_RELAY_TOKEN}, {@code MIB_API_SECRET}) : {@code Config.java} est suivi par git, ses
     * valeurs par défaut compilées doivent rester vides.</p>
     */
    public static final class Settings {

        /**
         * Interrupteur général de la synchronisation.
         */
        public boolean enabled;

        /**
         * Mode simulation : les deltas sont journalisés, rien n'est envoyé à Discord.
         */
        public boolean dryRun;

        /**
         * URL du relais, {@code wss://<host>/ws}.
         */
        public String wsUrl;

        /**
         * Jeton {@code DISCORD_BOT_RELAY_TOKEN}, envoyé dans le hello.
         */
        public String relayToken;

        /**
         * Base de l'API MIB ; la valeur de production sert de repli.
         */
        public String apiBaseUrl = MibApiClient.DEFAULT_BASE_URL;

        /**
         * Secret {@code DISCORD_BOT_API_SECRET}, en-tête {@code X-Bot-Secret}.
         */
        public String apiSecret;

        /**
         * Identifiant du rôle zMenuPremium. Doit être non nul, cf. {@link MibRoleManager#isConfigured()}.
         */
        public long premiumRoleId;

        /**
         * Identifiant du rôle zMenuPro. Doit être non nul, cf. {@link MibRoleManager#isConfigured()}.
         */
        public long proRoleId;

        /**
         * Période de la réconciliation complète ; 60 min si &lt;= 0.
         */
        public int reconcileIntervalMinutes = DEFAULT_RECONCILE_MINUTES;

        /**
         * Plafond anti-catastrophe des RÉVOCATIONS ; 200 si &lt;= 0. Les octrois ne sont jamais plafonnés.
         */
        public int maxRevocations = DEFAULT_MAX_REVOCATIONS;

        /**
         * Guilde sur laquelle les rôles sont appliqués.
         */
        public long guildId;
    }
}
