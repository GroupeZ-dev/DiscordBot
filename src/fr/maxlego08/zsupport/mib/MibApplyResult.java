package fr.maxlego08.zsupport.mib;

import java.util.List;

/**
 * Issue de l'application d'un palier sur un membre.
 *
 * <p>Les cinq issues sont volontairement distinctes : confondre « déjà conforme » et « appliqué »
 * rendrait l'idempotence invérifiable, et confondre « membre absent du serveur » et « échec »
 * ferait remonter du bruit permanent pour des clients qui ne sont simplement pas sur la guilde.</p>
 *
 * @param tier            le palier cible qui a été appliqué
 * @param outcome         l'issue de l'application
 * @param addedRoleIds    les rôles réellement ajoutés (ou qui l'auraient été en simulation)
 * @param removedRoleIds  les rôles réellement retirés (ou qui l'auraient été en simulation)
 * @param failure         la cause de l'échec, {@code null} sinon
 * @author Maxence
 */
public record MibApplyResult(MibTier tier, Outcome outcome, List<Long> addedRoleIds,
                             List<Long> removedRoleIds, String failure) {

    public MibApplyResult {
        addedRoleIds = addedRoleIds == null ? List.of() : List.copyOf(addedRoleIds);
        removedRoleIds = removedRoleIds == null ? List.of() : List.copyOf(removedRoleIds);
    }

    /**
     * Le membre portait déjà exactement le bon rôle : aucun appel REST n'a été émis.
     */
    public static MibApplyResult unchanged(MibTier tier) {
        return new MibApplyResult(tier, Outcome.UNCHANGED, List.of(), List.of(), null);
    }

    /**
     * Le delta a été appliqué sur Discord.
     */
    public static MibApplyResult applied(MibTier tier, List<Long> added, List<Long> removed) {
        return new MibApplyResult(tier, Outcome.APPLIED, added, removed, null);
    }

    /**
     * Mode simulation : le delta a été journalisé, rien n'a été envoyé à Discord.
     */
    public static MibApplyResult simulated(MibTier tier, List<Long> added, List<Long> removed) {
        return new MibApplyResult(tier, Outcome.SIMULATED, added, removed, null);
    }

    /**
     * Le membre n'est pas sur le serveur : no-op silencieux, le rattrapage se fera à son arrivée.
     */
    public static MibApplyResult unknownMember(MibTier tier) {
        return new MibApplyResult(tier, Outcome.UNKNOWN_MEMBER, List.of(), List.of(), null);
    }

    /**
     * Au moins un appel REST a échoué. Un retrait raté est ré-empilé par le manager, sans quoi le
     * membre conserverait les deux rôles et l'exclusivité du contrat serait violée.
     */
    public static MibApplyResult failed(MibTier tier, List<Long> added, List<Long> removed, String failure) {
        return new MibApplyResult(tier, Outcome.FAILED, added, removed, failure);
    }

    /**
     * @return vrai si un changement a été appliqué ou simulé
     */
    public boolean isChanged() {
        return this.outcome == Outcome.APPLIED || this.outcome == Outcome.SIMULATED;
    }

    /**
     * @return vrai si l'application n'a pas échoué (un membre absent n'est pas un échec)
     */
    public boolean isSuccess() {
        return this.outcome != Outcome.FAILED;
    }

    /**
     * @return une description lisible, destinée aux réponses de commande et aux journaux
     */
    public String describe() {
        return switch (this.outcome) {
            case UNCHANGED -> "déjà conforme (" + this.tier.getKey() + ")";
            case APPLIED -> "appliqué (" + this.tier.getKey() + ", ajout " + this.addedRoleIds + ", retrait " + this.removedRoleIds + ")";
            case SIMULATED -> "simulé (" + this.tier.getKey() + ", ajout " + this.addedRoleIds + ", retrait " + this.removedRoleIds + ")";
            case UNKNOWN_MEMBER -> "membre absent du serveur, aucun changement";
            case FAILED -> "échec : " + (this.failure == null ? "cause inconnue" : this.failure);
        };
    }

    public enum Outcome {
        UNCHANGED,
        APPLIED,
        SIMULATED,
        UNKNOWN_MEMBER,
        FAILED
    }
}
