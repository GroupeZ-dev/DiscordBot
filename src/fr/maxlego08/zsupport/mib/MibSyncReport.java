package fr.maxlego08.zsupport.mib;

import java.util.concurrent.TimeUnit;

/**
 * Bilan d'une passe de réconciliation (docs/discord-tier-sync.md §7.2).
 *
 * <p>Les octrois, les corrections et les révocations sont comptés <b>séparément</b> parce que seules
 * les révocations sont plafonnées : au premier déploiement l'écart est constitué de centaines
 * d'octrois légitimes, et plafonner les deux ferait abandonner chaque passe sans jamais converger.</p>
 *
 * @param status             issue de la passe
 * @param message            précision lisible (cause d'annulation, anomalies non bloquantes)
 * @param dryRun             vrai si la passe n'a rien envoyé à Discord
 * @param entitled           ayants droit renvoyés par l'instantané paginé
 * @param holders            porteurs locaux d'un des deux rôles, lus dans le cache JDA
 * @param grants             membres qui reçoivent un rôle sans en porter aucun
 * @param corrections        membres qui changent de palier (retrait de l'un, ajout de l'autre)
 * @param revocations        membres qui retombent en {@code free} et perdent leurs deux rôles
 * @param skippedRevocations révocations abandonnées parce que le plafond a été atteint
 * @param unknownMembers     comptes ciblés qui ne sont pas sur la guilde
 * @param durationMillis     durée de la passe
 * @author Maxence
 */
public record MibSyncReport(Status status, String message, boolean dryRun, int entitled, int holders,
                            int grants, int corrections, int revocations, int skippedRevocations,
                            int unknownMembers, long durationMillis) {

    /**
     * Passe menée à son terme.
     *
     * @param startedNanos horodatage {@link System#nanoTime()} pris au début de la passe ; la durée
     *                     n'est jamais dérivée de deux horloges murales, qui peuvent reculer
     */
    public static MibSyncReport success(boolean dryRun, int entitled, int holders, int grants, int corrections,
                                        int revocations, int skippedRevocations, int unknownMembers,
                                        String message, long startedNanos) {
        return new MibSyncReport(Status.SUCCESS, message == null ? "" : message, dryRun, entitled, holders,
                grants, corrections, revocations, skippedRevocations, unknownMembers, elapsed(startedNanos));
    }

    /**
     * Passe <b>annulée</b> : aucune révocation n'a été appliquée. Toute erreur d'API, toute pagination
     * incomplète et tout corps illisible passent par ici.
     */
    public static MibSyncReport failed(String reason, long startedNanos) {
        return new MibSyncReport(Status.FAILED, reason == null ? "cause inconnue" : reason, false,
                0, 0, 0, 0, 0, 0, 0, elapsed(startedNanos));
    }

    /**
     * Passe non lancée (synchronisation désactivée, ou passe déjà différée par l'anti-rebond).
     */
    public static MibSyncReport skipped(String reason) {
        return new MibSyncReport(Status.SKIPPED, reason == null ? "" : reason, false,
                0, 0, 0, 0, 0, 0, 0, 0L);
    }

    private static long elapsed(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    public boolean isSuccess() {
        return this.status == Status.SUCCESS;
    }

    /**
     * @return le nombre total de changements planifiés par la passe
     */
    public int changes() {
        return this.grants + this.corrections + this.revocations;
    }

    /**
     * @return un résumé sur une ligne, destiné à la console et aux réponses de commande
     */
    public String toSummary() {
        return switch (this.status) {
            case SKIPPED -> "Passe ignorée : " + this.message;
            case FAILED -> "Passe ANNULÉE : " + this.message;
            case SUCCESS -> (this.dryRun ? "[simulation] " : "")
                    + "ayants droit " + this.entitled + ", porteurs " + this.holders
                    + " -> octrois " + this.grants + ", corrections " + this.corrections
                    + ", révocations " + this.revocations
                    + (this.skippedRevocations > 0 ? " (" + this.skippedRevocations + " ABANDONNÉES, plafond atteint)" : "")
                    + ", membres absents " + this.unknownMembers
                    + ", en " + this.durationMillis + " ms"
                    + (this.message == null || this.message.isEmpty() ? "" : " — " + this.message);
        };
    }

    public enum Status {
        SUCCESS,
        FAILED,
        SKIPPED
    }
}
