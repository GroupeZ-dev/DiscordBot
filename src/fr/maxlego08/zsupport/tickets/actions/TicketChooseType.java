package fr.maxlego08.zsupport.tickets.actions;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.lang.LangType;
import fr.maxlego08.zsupport.lang.Message;
import fr.maxlego08.zsupport.tickets.TicketStatus;
import fr.maxlego08.zsupport.tickets.TicketType;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.PermissionOverrideAction;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

public class TicketChooseType extends TicketAction {

    @Override
    public void process(Interaction interaction) {

        PermissionOverrideAction permissionOverrideAction = textChannel.upsertPermissionOverride(this.member);
        permissionOverrideAction.setAllowed(Permission.VIEW_CHANNEL).queue(e -> {

            String welcomeMessage = getMessage(ticket.getLangType(), Message.TICKET_WELCOME, user.getAsMention());
            textChannel.sendMessage(welcomeMessage).queue();

            Emoji emoji = Config.getSpigotEmoji(guild);
            EmbedBuilder builder = this.createEmbed();

            if (this.ticket.getLangType() == LangType.US) {

                setDescription(builder,
                        "**Please choose the type of your ticket**:",
                        ":white_check_mark: To verify your purchase",
                        ":question: To request help on a plugin",
                        emoji.getFormatted() + " To request access on spigot",
                        ":wave: Questions before purchase",
                        "",
                        "```ansi\n" +
                                "\u001B[2;31mPlease choose your ticket type correctly. Your ticket may be closed if your request does not match the ticket type.\u001B[0m\n" +
                                "```"
                );
            } else {
                setDescription(builder,
                        "**Veuillez choisir le type de votre ticket**:",
                        ":white_check_mark: Pour vérifier votre achat",
                        ":question: Pour demander de l'aide sur un plugin",
                        emoji.getFormatted() + " Pour demander l'accès sur spigot",
                        ":wave: Questions avant achat",
                        "",
                        "```ansi\n" +
                                "\u001B[2;31mVeuillez choisir correctement le type de votre ticket. Votre ticket peut être fermé si votre demande ne correspond pas au type de ticket.\u001B[0m\n" +
                                "```"
                );
            }


            MessageCreateAction action = textChannel.sendMessageEmbeds(builder.build());

            Button buttonOrder = new ButtonImpl(BUTTON_CHOOSE_VERIFY,
                    "Verify Purchase", ButtonStyle.SUCCESS, false,
                    Emoji.fromUnicode("U+1F4B5"));

            Button buttonHelp = new ButtonImpl(BUTTON_CHOOSE_SUPPORT,
                    getMessage(this.ticket.getLangType(), Message.TICKET_CHOOSE_PLUGIN), ButtonStyle.PRIMARY, false,
                    Emoji.fromUnicode("U+2753"));

            Button buttonSpigot = new ButtonImpl(BUTTON_CHOOSE_SPIGOT,
                    getMessage(this.ticket.getLangType(), Message.TICKET_CHOOSE_SPIGOT), ButtonStyle.SECONDARY, false,
                    emoji);

            Button buttonBeforePurchase = new ButtonImpl(BUTTON_CHOOSE_BEFORE_PURCHASE,
                    getMessage(this.ticket.getLangType(), Message.TICKET_CHOOSE_BEFORE_PURCHASE), ButtonStyle.SECONDARY, false,
                    Emoji.fromUnicode("U+1F44B"));

            action.setActionRow(buttonOrder, buttonHelp, buttonSpigot, buttonBeforePurchase, createCloseButton());
            action.queue(result -> {
                permissionOverrideAction.setAllowed(Permission.VIEW_CHANNEL).queue();
            });

        });

    }

    @Override
    public void onButton(ButtonInteractionEvent event) {

        String buttonId = event.getButton().getId();
        if (buttonId == null) {
            event.reply("Impossible de trouver l'intéraction.").setEphemeral(true).queue();
            return;
        }

        switch (buttonId) {

            case BUTTON_CHOOSE_SUPPORT -> {
                ticket.setTicketType(TicketType.PLUGIN);
                processNextAction(TicketStatus.CHOOSE_PLUGIN);
            }
            case BUTTON_CHOOSE_VERIFY -> {
                ticket.setTicketType(TicketType.VERIFICATION);
                processNextAction(TicketStatus.VERIFY_PURCHASE_PLUGIN);
            }
            case BUTTON_CHOOSE_SPIGOT -> {
                ticket.setTicketType(TicketType.SPIGOT);
                processNextAction(TicketStatus.SPIGOT_ACCESS_MODAL);
            }
            case BUTTON_CHOOSE_BEFORE_PURCHASE -> {
                ticket.setTicketType(TicketType.QUESTION);
                processNextAction(TicketStatus.QUESTION_MODAL);
            }

            default -> event.reply("We can’t find the button.").setEphemeral(true).queue();
        }

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
        return TicketStatus.CHOOSE_TYPE;
    }
}
