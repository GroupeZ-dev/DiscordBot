package fr.maxlego08.zsupport.command.commands;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.command.CommandType;
import fr.maxlego08.zsupport.command.VCommand;
import fr.maxlego08.zsupport.tickets.Ticket;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;

import java.util.Optional;

public class CommandPauseAutoClose extends VCommand {

    public CommandPauseAutoClose(CommandManager commandManager) {
        super(commandManager);
        this.permission = Permission.MESSAGE_MANAGE;
        this.consoleCanUse = false;
        this.onlyInCommandChannel = false;
        this.description = "Toggle auto-close for a ticket";
    }

    @Override
    protected CommandType perform(ZSupport main) {

        if (!(event.getChannel() instanceof ICategorizableChannel iCategorizableChannel) || iCategorizableChannel.getParentCategoryIdLong() != Config.ticketCategoryId) {
            event.reply(":x: You can only use this command inside a ticket channel.").setEphemeral(true).queue();
            return CommandType.SUCCESS;
        }

        Optional<Ticket> optional = main.getTicketManager().getByChannel(event.getChannel(), event.getGuild());

        if (optional.isEmpty()) {
            event.reply(":x: Unable to find the ticket for this channel.").setEphemeral(true).queue();
            return CommandType.SUCCESS;
        }

        Ticket ticket = optional.get();
        main.getTicketManager().toggleAutoClose(ticket);

        if (ticket.isAutoCloseDisabled()) {
            event.reply(":pause_button: Auto-close has been **disabled** for this ticket. The ticket will stay open until manually closed.").queue();
        } else {
            event.reply(":arrow_forward: Auto-close has been **re-enabled** for this ticket.").queue();
        }

        return CommandType.SUCCESS;
    }
}
