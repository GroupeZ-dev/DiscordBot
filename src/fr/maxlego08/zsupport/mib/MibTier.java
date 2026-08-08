package fr.maxlego08.zsupport.mib;

import java.util.Locale;

/**
 * Palier d'abonnement Minecraft Inventory Builder d'un compte Discord.
 *
 * <p>Les trois clés sont exactement celles de {@code UiBuilderPlanService} côté Laravel
 * (docs/discord-tier-sync.md §1). Aucun mot nouveau n'est introduit ici : le moindre synonyme
 * ferait diverger les deux dépôts sans qu'aucun compilateur ne s'en aperçoive.</p>
 *
 * <p>Le mapping vers les rôles Discord est <b>exclusif</b> : un membre ne porte jamais zMenuPremium
 * et zMenuPro en même temps. Un palier est un état cible absolu, jamais un delta, ce qui rend son
 * application idempotente.</p>
 *
 * @author Maxence
 */
public enum MibTier {

    FREE("free", 0),
    PREMIUM("premium", 1),
    ULTIMATE("ultimate", 2);

    private final String key;
    private final int rank;

    MibTier(String key, int rank) {
        this.key = key;
        this.rank = rank;
    }

    /**
     * Convertit une clé JSON en palier, en <b>fail-closed</b>.
     *
     * <p>Une clé nulle, vide ou inconnue retombe volontairement sur {@link #FREE}. Si MIB changeait un
     * jour son vocabulaire, le bot doit cesser d'accorder des rôles — jamais se mettre à en accorder à
     * tout le monde. Le cas {@code "free"} est écrit explicitement, et non laissé au {@code default},
     * pour qu'aucune relecture ne puisse confondre « valeur connue » et « valeur de repli ».</p>
     *
     * @param key la valeur du champ {@code tier} renvoyée par MIB
     * @return le palier correspondant, {@link #FREE} en cas de doute
     */
    public static MibTier fromKey(String key) {
        if (key == null) return FREE;
        return switch (key.trim().toLowerCase(Locale.ROOT)) {
            case "free" -> FREE;
            case "premium" -> PREMIUM;
            case "ultimate" -> ULTIMATE;
            default -> FREE;
        };
    }

    /**
     * @return la clé JSON du palier, telle qu'elle circule sur l'API MIB
     */
    public String getKey() {
        return this.key;
    }

    /**
     * @return le rang du palier (free 0 &lt; premium 1 &lt; ultimate 2)
     */
    public int getRank() {
        return this.rank;
    }

    /**
     * @return vrai si le palier ouvre le droit au support zMenu (sémantique de {@code can_open})
     */
    public boolean isEntitled() {
        return this != FREE;
    }

    /**
     * Rôle Discord unique que ce palier doit poser sur le membre.
     *
     * <p>Le mapping est exhaustif et sa valeur par défaut est « aucun rôle » : le ternaire historique
     * {@code power == PREMIUM_POWER ? zMenuPremium : zMenuPro} accordait au contraire le rôle le plus
     * élevé dès que le palier n'était pas reconnu, c'est-à-dire fail-open.</p>
     *
     * @param premiumRoleId identifiant du rôle zMenuPremium
     * @param proRoleId     identifiant du rôle zMenuPro
     * @return l'identifiant du rôle à poser, {@code 0} pour {@link #FREE}
     */
    public long roleIdFor(long premiumRoleId, long proRoleId) {
        return switch (this) {
            case FREE -> 0L;
            case PREMIUM -> premiumRoleId;
            case ULTIMATE -> proRoleId;
        };
    }
}
