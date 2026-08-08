package fr.maxlego08.zsupport.command.commands;

import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.command.CommandArgument;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.command.CommandType;
import fr.maxlego08.zsupport.command.VCommand;
import fr.maxlego08.zsupport.mib.MibApplyResult;
import fr.maxlego08.zsupport.mib.MibRoleManager;
import fr.maxlego08.zsupport.mib.MibSyncReport;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.awt.Color;
import java.util.ArrayList;

/**
 * Déclenche à la main la synchronisation des paliers MIB (docs/discord-tier-sync.md §9).
 *
 * <p>Sans argument : une réconciliation complète. Avec un utilisateur : une résolution unitaire suivie
 * de l'application de son palier.</p>
 *
 * <p>La réponse est <b>éphémère</b> : le bilan expose combien de clients paient et combien perdent
 * leur rôle, ce n'est pas une information de salon public.</p>
 *
 * @author Maxence
 */
public class CommandMibSync extends VCommand {

    private static final String OPTION_USER = "utilisateur";

    private static final Color COLOR_SUCCESS = new Color(45, 150, 45);
    private static final Color COLOR_WARNING = new Color(230, 182, 25);
    private static final Color COLOR_FAILURE = new Color(218, 8, 8);

    public CommandMibSync(CommandManager commandManager) {
        super(commandManager);
        this.consoleCanUse = false;
        this.onlyInCommandChannel = false;
        this.description = "Synchronise les rôles zMenuPremium / zMenuPro depuis Minecraft Inventory Builder";
        this.permission = Permission.MANAGE_ROLES;
        this.addOptionalArg(new CommandArgument(OptionType.USER, OPTION_USER,
                "Membre à resynchroniser ; sans lui, réconciliation complète", new ArrayList<>()));
    }

    @Override
    protected CommandType perform(ZSupport main) {

        // Les instances de VCommand sont enregistrées une fois et réutilisées à chaque invocation :
        // this.guild et this.event sont écrasés par l'appel suivant. Tout ce dont le traitement
        // asynchrone a besoin est donc capturé maintenant, dans des variables locales.
        Guild guild = this.guild;
        MibRoleManager manager = MibRoleManager.getInstance();

        OptionMapping option = this.event.getOption(OPTION_USER);
        User target = option == null ? null : option.getAsUser();

        // Une passe complète interroge MIB page par page : bien au-delà des 3 s d'une réponse
        // d'interaction. On accuse réception tout de suite et on édite le message ensuite.
        this.event.deferReply(true).queue(hook -> {
            if (target == null) {
                reconcile(manager, guild, hook);
            } else {
                syncMember(manager, guild, hook, target);
            }
        });

        return CommandType.SUCCESS;
    }

    /**
     * Réconciliation complète. Le manager répond lui-même un bilan {@code SKIPPED} si la
     * synchronisation est désactivée ou si une passe est déjà différée par l'anti-rebond.
     */
    private void reconcile(MibRoleManager manager, Guild guild, InteractionHook hook) {
        manager.reconcileAsync(report -> {

            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle("MIB - réconciliation des paliers");
            setEmbedFooter(guild, builder, color(report));
            setDescription(builder, report.toSummary());
            addStateField(builder, manager);

            if (report.isSuccess() && report.dryRun()) {
                builder.addField("Simulation", "Aucun rôle n'a été touché. Passer `mibDryRun` à `false` pour appliquer.", false);
            }

            hook.editOriginalEmbeds(builder.build()).queue();
        });
    }

    /**
     * Résolution unitaire puis application.
     *
     * <p>Le garde {@code isStarted} n'est pas décoratif : sans lui, la commande appliquerait des rôles
     * alors que la synchronisation automatique est éteinte, et la prochaine réconciliation les
     * retirerait — ou, pire, elle interrogerait MIB sans secret et n'obtiendrait que des 401.</p>
     */
    private void syncMember(MibRoleManager manager, Guild guild, InteractionHook hook, User target) {

        if (!manager.isStarted()) {
            EmbedBuilder builder = new EmbedBuilder();
            builder.setTitle("MIB - synchronisation désactivée");
            setEmbedFooter(guild, builder, COLOR_WARNING);
            setDescription(builder, ":warning: La synchronisation des paliers n'est pas démarrée.",
                    "Vérifier `mibRoleSyncEnabled`, les identifiants de rôle et les secrets (`MIB_RELAY_TOKEN`, `MIB_API_SECRET`).");
            hook.editOriginalEmbeds(builder.build()).queue();
            return;
        }

        manager.syncMemberAsync(target.getIdLong(),
                result -> hook.editOriginalEmbeds(memberEmbed(guild, manager, target, result).build()).queue(),
                throwable -> {
                    EmbedBuilder builder = new EmbedBuilder();
                    builder.setTitle("MIB - " + target.getName());
                    setEmbedFooter(guild, builder, COLOR_FAILURE);
                    // Une panne n'est pas un refus : on le dit explicitement, sinon un modérateur
                    // conclurait que le client ne paie pas.
                    setDescription(builder, ":x: MIB n'a pas pu être interrogé, le palier est **inconnu** (ce n'est pas un refus).",
                            "```" + throwable + "```");
                    hook.editOriginalEmbeds(builder.build()).queue();
                });
    }

    private EmbedBuilder memberEmbed(Guild guild, MibRoleManager manager, User target, MibApplyResult result) {

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("MIB - " + target.getName());
        setEmbedFooter(guild, builder, result.isSuccess() ? COLOR_SUCCESS : COLOR_FAILURE);
        setDescription(builder, (result.isSuccess() ? ":white_check_mark: " : ":x: ") + result.describe());
        builder.addField("Palier", result.tier().getKey(), true);
        builder.addField("Simulation", manager.getSettings().dryRun ? "oui" : "non", true);
        return builder;
    }

    private void addStateField(EmbedBuilder builder, MibRoleManager manager) {
        builder.addField("Relais", manager.isRelayConnected() ? "connecté" : "déconnecté", true);
        builder.addField("API", manager.getApiClient().getBaseUrl(), true);
    }

    private Color color(MibSyncReport report) {
        return switch (report.status()) {
            case SUCCESS -> report.skippedRevocations() > 0 ? COLOR_WARNING : COLOR_SUCCESS;
            case SKIPPED -> COLOR_WARNING;
            case FAILED -> COLOR_FAILURE;
        };
    }
}
