package fr.maxlego08.zsupport.tickets.actions;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.tickets.TicketStatus;
import fr.maxlego08.zsupport.verify.VerifyManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.Interaction;

import java.awt.*;
import java.util.concurrent.TimeUnit;

public class TicketZMenuVerification extends TicketAction {
    @Override
    public void process(Interaction interaction) {

        EmbedBuilder builder = new EmbedBuilder();
        setEmbedFooter(this.guild, builder, new Color(230, 182, 25));
        setDescription(builder, ":gear: Check your purchase, please wait.");

        updatePermission(r -> {
            textChannel.sendMessageEmbeds(builder.build()).setActionRow(createCloseButton()).queueAfter(1, TimeUnit.SECONDS, editMessage -> {

                if (!hasRole(this.member, Config.zMenuPremium) && !hasRole(this.member, Config.zMenuPro)) {

                    VerifyManager verifyManager = VerifyManager.getInstance();

                    verifyManager.verifyMinecraftInventoryUser(this.user, textChannel, mib -> {

                        editMessage.delete().queue();

                        // PANNE MIB (timeout, 401, 5xx, corps illisible) : ce n'est PAS un refus. La
                        // branche de refus supprime le salon au bout d'une minute, une erreur réseau
                        // ferait donc perdre son ticket à un client payant. On route vers la
                        // validation humaine (docs/discord-tier-sync.md §7.3).
                        if (mib.technicalError()) {
                            processNextAction(TicketStatus.PLUGIN_VERIFY_NEED_INFORMATION);
                            return;
                        }

                        if (!mib.hasAccess()) {
                            processNextAction(TicketStatus.VERIFY_ZMENU_CLOSE);
                            return;
                        }

                        // Mapping exhaustif avec défaut « aucun rôle ». Le ternaire historique
                        // (power == PREMIUM_POWER ? zMenuPremium : zMenuPro) accordait au contraire le
                        // rôle le PLUS ÉLEVÉ dès que le palier n'était pas reconnu : fail-open.
                        long roleId = mib.tier().roleIdFor(Config.zMenuPremium, Config.zMenuPro);
                        Role role = roleId == 0L ? null : guild.getRoleById(roleId);
                        if (role != null) guild.addRoleToMember(this.member, role).queue();

                        processNextAction(TicketStatus.PLUGIN_INFORMATION);
                    });
                    return;
                }

                editMessage.delete().queueAfter(1, TimeUnit.SECONDS, r2 -> {
                    processNextAction(TicketStatus.PLUGIN_INFORMATION);
                });
            });
        }, Permission.VIEW_CHANNEL);

        sendVacationInformation();
    }

    @Override
    public void onButton(ButtonInteractionEvent event) {

    }

    @Override
    public void onSelect(StringSelectInteractionEvent event) {

    }

    @Override
    public void onModal(ModalInteractionEvent event) {

    }

    @Override
    public void onMessage(MessageReceivedEvent event) {

    }

    @Override
    public TicketStatus getTicketStatus() {
        return TicketStatus.VERIFY_ZMENU_PURCHASE;
    }
}
