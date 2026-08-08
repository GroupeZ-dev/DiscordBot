package fr.maxlego08.zsupport.verify;

import fr.maxlego08.zsupport.mib.MibTier;

/**
 * Réponse de Minecraft Inventory Builder pour le flux ticket zMenu (docs/discord-tier-sync.md §7.3).
 *
 * <p><b>Trois états, pas deux.</b> {@code technicalError} distingue une <i>panne</i> (timeout, 401,
 * 5xx, corps illisible) d'un <i>refus légitime</i>. C'est indispensable : la branche de refus
 * supprime le salon du ticket au bout d'une minute. Une panne MIB doit router vers la validation
 * humaine, jamais vers la fermeture — c'est exactement ce que faisait l'ancien
 * {@code MIB(false, 0)} de repli, qui envoyait tous les tickets zMenu, clients payants compris, à la
 * poubelle dès que l'appel échouait (et il échouait toujours, cf. §5).</p>
 *
 * <p>Le champ {@code power} a disparu au profit du palier : il n'existe plus dans la réponse de MIB,
 * et le lire produisait un {@code NullPointerException}.</p>
 *
 * @param tier           le palier du compte, jamais {@code null}
 * @param hasAccess      le droit d'ouvrir un ticket zMenu ({@code can_open})
 * @param technicalError vrai si MIB n'a pas pu être interrogé, ce qui n'est pas un refus
 * @author Maxence
 */
public record MIB(boolean hasAccess, MibTier tier, boolean technicalError) {

    public MIB {
        tier = tier == null ? MibTier.FREE : tier;
    }

    /**
     * MIB a répondu, et ce compte n'a pas le droit d'ouvrir un ticket zMenu.
     */
    public static MIB denied() {
        return new MIB(false, MibTier.FREE, false);
    }

    /**
     * MIB a répondu et accorde l'ouverture.
     *
     * <p>Le palier peut valoir {@link MibTier#FREE} si MIB renvoie une clé que le bot ne connaît pas :
     * le mapping vers les rôles retombe alors sur « aucun rôle », qui est le bon défaut. Accorder le
     * rôle le plus élevé dans ce cas serait un fail-open.</p>
     */
    public static MIB granted(MibTier tier) {
        return new MIB(true, tier, false);
    }

    /**
     * MIB n'a pas pu être interrogé. À router vers la validation humaine, jamais vers la fermeture.
     */
    public static MIB technicalFailure() {
        return new MIB(false, MibTier.FREE, true);
    }
}
