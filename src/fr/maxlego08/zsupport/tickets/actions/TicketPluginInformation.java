package fr.maxlego08.zsupport.tickets.actions;

import fr.maxlego08.zsupport.tickets.TicketStatus;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.Interaction;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.ComponentInteraction;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TicketPluginInformation extends TicketAction {

    private static final Pattern TRIPLE_BACKTICK_PATTERN = Pattern.compile("```([^`]*)```", Pattern.MULTILINE);
    private static final Pattern DOUBLE_BACKTICK_PATTERN = Pattern.compile("``([^`]*)``", Pattern.MULTILINE);
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]*)`");
    private static final Pattern PATTERN_URL = Pattern.compile("((http|https)://)?(www\\.)?[a-zA-Z0-9\\-]+\\.[a-z]{2,6}(/[\\w\\-./?%&=]*)?");

    @Override
    public void process(Interaction interaction) {

        EmbedBuilder builder = createEmbed();
        setDescription(builder, "Please fill in the form to continue with the ticket.");

        Button openForm = new ButtonImpl(BUTTON_OPEN_MODAL_PLUGIN, "Open the form", ButtonStyle.SECONDARY, false, Emoji.fromUnicode("U+1F44B"));

        this.textChannel.sendMessageEmbeds(builder.build()).setActionRow(openForm, createCloseButton()).queue();

        if (interaction != null && !interaction.isAcknowledged() && interaction instanceof ComponentInteraction componentInteraction) {
            componentInteraction.getMessage().delete().queue();
        }
    }

    private void openModal() {
        TextInput pluginVersion = TextInput.create("pluginVersion", "Plugin Version", TextInputStyle.SHORT).setPlaceholder("Enter the version of your plugin here").setMinLength(3).setMaxLength(50).build();

        TextInput serverVersion = TextInput.create("serverVersion", "Server Version", TextInputStyle.SHORT).setPlaceholder("Enter your server version here").setMinLength(3).setMaxLength(50).build();

        TextInput problemDescription = TextInput.create("description", "Description of the problem", TextInputStyle.PARAGRAPH).setPlaceholder("Describe the problem you are experiencing").setMinLength(10).setMaxLength(4000).build();

        Modal modal = Modal.create(MODAL_PLUGIN_INFORMATIONS, "Informations").addComponents(ActionRow.of(pluginVersion), ActionRow.of(serverVersion), ActionRow.of(problemDescription)).build();

        replyModal(modal);
    }

    @Override
    public void onButton(ButtonInteractionEvent event) {
        if (Objects.equals(event.getButton().getId(), BUTTON_OPEN_MODAL_PLUGIN)) {
            if (isAuthor()) openModal();
            else event.reply(":x: Only the ticket author to access the modal.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onSelect(StringSelectInteractionEvent event) {

    }

    @Override
    public void onModal(ModalInteractionEvent event) {

        if (!event.getModalId().equals(MODAL_PLUGIN_INFORMATIONS)) return;

        String pluginVersion = event.getValue("pluginVersion").getAsString();
        String serverVersion = event.getValue("serverVersion").getAsString();
        String description = event.getValue("description").getAsString();

        EmbedBuilder builder = createEmbed();
        setDescription(builder, "**Plugin Version**:", "```" + pluginVersion + "```", "**Server Version**:", "```" + serverVersion + "```", "**Description**:", "```" + description.replace("`", "") + "```", "", "You can send additional information, logs, configurations, or other information to help your problem.", "```ansi\n" + "\u001B[2;31m\u001B[1;31mIn order for the support response to be effective, please provide all the requested information.\u001B[0m\u001B[2;31m\u001B[0m\n" + "```");

        this.textChannel.sendMessageEmbeds(builder.build()).setActionRow(createCloseButton()).queue(message -> processNextAction(TicketStatus.OPEN));

        event.reply("For the support response to be effective, please provide all the requested information.").setEphemeral(true).queue(response -> {

            Message message = event.getMessage();
            if (message != null) message.delete().queueAfter(3, TimeUnit.SECONDS);

            sendLinks(description);
            sendCodes(description);
        });

        this.ticketManager.verifyVersion(this.ticket, this.textChannel, this.guild, pluginVersion);

        sendVacationInformation();
    }

    private void sendCodes(String description) {
        var markdownFormats = detectMarkdownFormat(description);
        for (String markdownFormat : markdownFormats) {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Configuration Or Code");
            stringBuilder.append("\n");
            stringBuilder.append(markdownFormat);

            this.textChannel.sendMessage(stringBuilder).queueAfter(550, TimeUnit.MILLISECONDS);
        }
    }

    private void sendLinks(String description) {
        var links = extractLinks(description);
        if (!links.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String link : links) {
                String displayLink = link;
                if (link.startsWith("http://")) {
                    displayLink = link.substring(7);
                } else if (link.startsWith("https://")) {
                    displayLink = link.substring(8);
                }
                if (!link.startsWith("http")) {
                    link = "https://" + link;
                }
                sb.append("* [").append(displayLink).append("](").append(link).append(")\n");
            }
            String list = sb.toString();
            if (!list.isEmpty()) {
                EmbedBuilder embedBuilder = createEmbed();
                setDescription(embedBuilder, "The following links have been detected in your message:\n" + list);
                this.textChannel.sendMessageEmbeds(embedBuilder.build()).queueAfter(500, TimeUnit.MILLISECONDS);
            }
        }

    }

    @Override
    public TicketStatus getTicketStatus() {
        return TicketStatus.PLUGIN_INFORMATION;
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {

    }

    private List<String> extractLinks(String input) {
        List<String> links = new ArrayList<>();
        Matcher matcher = PATTERN_URL.matcher(input);

        while (matcher.find()) {
            String link = matcher.group();
            if (link.endsWith(".") || link.endsWith(",") || link.endsWith(";")) {
                link = link.substring(0, link.length() - 1);
            }
            links.add(link);
        }

        return links;
    }

    private List<String> detectMarkdownFormat(String text) {
        List<String> detectedCodes = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder(text);

        detectAndRemove(textBuilder, TRIPLE_BACKTICK_PATTERN, detectedCodes);
        detectAndRemove(textBuilder, DOUBLE_BACKTICK_PATTERN, detectedCodes);
        detectAndRemove(textBuilder, INLINE_CODE_PATTERN, detectedCodes);

        return detectedCodes;
    }

    private void detectAndRemove(StringBuilder textBuilder, Pattern pattern, List<String> detectedCodes) {
        Matcher matcher = pattern.matcher(textBuilder);
        while (matcher.find()) {
            detectedCodes.add(matcher.group(0));
            textBuilder.delete(matcher.start(), matcher.end());
            matcher = pattern.matcher(textBuilder);
        }
    }


}
