package fr.maxlego08.zsupport.tickets;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.lang.LangType;
import fr.maxlego08.zsupport.utils.ChannelType;
import fr.maxlego08.zsupport.utils.Constant;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class TicketListener extends ListenerAdapter implements Constant {

    private final Map<Long, Integer> pingAmounts = new HashMap<>();
    private final Map<String, Long> lastInformationsMessage = new HashMap<>();
    private final TicketManager ticketManager;
    private final ErrorManager manager = new ErrorManager();

    public TicketListener(TicketManager ticketManager) {
        this.ticketManager = ticketManager;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {

        Button button = event.getButton();

        if (event.getChannel().getIdLong() == Config.ticketChannel && !event.getUser().isBot()) {

            LangType langType = Objects.equals(button.getId(), BUTTON_FR) ? LangType.FR : LangType.US;
            TicketStatus ticketStatus = button.getId().equals(BUTTON_ZMENU) ? TicketStatus.VERIFY_ZMENU_PURCHASE : TicketStatus.CHOOSE_TYPE;

            this.ticketManager.createTicket(event.getUser(), event.getGuild(), langType, event, ticketStatus);
        } else if (event.getChannel() instanceof ICategorizableChannel iCategorizableChannel && iCategorizableChannel.getParentCategoryIdLong() == Config.ticketCategoryId && !event.getUser().isBot()) {

            this.ticketManager.buttonAction(event, event.getGuild());
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (event.getChannel() instanceof ICategorizableChannel iCategorizableChannel && iCategorizableChannel.getParentCategoryIdLong() == Config.ticketCategoryId && !event.getUser().isBot()) {

            this.ticketManager.selectionAction(event, event.getGuild());
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        if (event.getChannel() instanceof ICategorizableChannel iCategorizableChannel && iCategorizableChannel.getParentCategoryIdLong() == Config.ticketCategoryId && !event.getUser().isBot()) {

            this.ticketManager.modalAction(event, event.getGuild());
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {

        MessageChannel channel = event.getChannel();
        Message message = event.getMessage();
        Member member = event.getMember();

        if (event.getAuthor().isBot() || member == null) return;

        // General help
        if (channel.getIdLong() == Config.generalChannel) {
            this.manager.processMessage(event, message, event.getAuthor());
        }

        // DONT PING STAFF OMG
        if (message.getMentions().getMembers().stream().anyMatch(e -> e.hasPermission(Permission.MESSAGE_MANAGE) && !e.getUser().isBot()) && !member.hasPermission(Permission.MESSAGE_MANAGE)) {

            if (member.getRoles().stream().anyMatch(role ->
                    role.getIdLong() == 1203368378581000192L
                            || role.getIdLong() == 1203368206337708113L
                            || role.getIdLong() == 1186657702076743770L
                            || role.getIdLong() == 511544969245491223L
                            || role.getIdLong() == 1094539124200976508L
                            || role.getIdLong() == 1223998091968118855L
                            || role.getIdLong() == 1276921293995905096L // Friendly developer
            )) {
                return;
            }

            int amount = this.pingAmounts.getOrDefault(member.getIdLong(), 0) + 1;

            long duration = 2L * amount;
            var ruleChannel = event.getGuild().getTextChannelById(Config.ruleChannel);

            message.reply(":rage: Please respect the discord " + (ruleChannel == null ? "rules" : ruleChannel.getAsMention()) + " ! You must not ping a team member. You just timeout " + duration + " minutes ! (x" + amount + ")").queue(m -> m.delete().queueAfter(30, TimeUnit.SECONDS));
            event.getGuild().timeoutFor(member, Duration.ofMinutes(duration)).queue();

            this.pingAmounts.put(member.getIdLong(), amount);
        }

        this.ticketManager.processMessageUpload(event);
        this.ticketManager.processZMenuForumMessage(event);

        if (event.getChannel() instanceof ICategorizableChannel iCategorizableChannel && iCategorizableChannel.getParentCategoryIdLong() == Config.ticketCategoryId) {

            this.ticketManager.onMessage(event, event.getGuild());
        } else if (Config.channelsWithInformations.containsKey(channel.getIdLong()) && channel instanceof TextChannel textChannel) {

            ChannelType channelType = Config.channelsWithInformations.get(channel.getIdLong());

            if (channelType == ChannelType.PREMIUM) {

                if (member.hasPermission(Permission.MANAGE_ROLES)) return;

                // String key = channel.getIdLong() + "-" + member.getIdLong();
                String key = channel.getIdLong() + "";
                long last = this.lastInformationsMessage.getOrDefault(key, 0L);
                if (System.currentTimeMillis() - last < 1000 * 60 * 5) return;
                this.lastInformationsMessage.put(key, System.currentTimeMillis());

                message.reply(channelType.getDescription()).mentionRepliedUser(false).queue(m2 -> m2.delete().queueAfter(10, TimeUnit.SECONDS));

            } else {
                this.ticketManager.sendChannelInformations(event, textChannel, channelType);
            }
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        this.ticketManager.userLeave(event.getGuild(), event.getUser());
    }
}
