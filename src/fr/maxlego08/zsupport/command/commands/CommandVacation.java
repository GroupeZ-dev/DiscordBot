package fr.maxlego08.zsupport.command.commands;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.api.Vacation;
import fr.maxlego08.zsupport.command.CommandChoice;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.command.CommandType;
import fr.maxlego08.zsupport.command.VCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;

import java.util.List;
import java.util.Objects;

public class CommandVacation extends VCommand {

    public CommandVacation(CommandManager commandManager) {
        super(commandManager);
        this.consoleCanUse = false;
        this.permission = Permission.ADMINISTRATOR;
        this.description = "Set the dates of the holidays of Maxlego08 !";
        var choices = List.of(
                new CommandChoice("Maintenant", String.valueOf(System.currentTimeMillis())),
                new CommandChoice("Demain", String.valueOf(System.currentTimeMillis() + (86400 * 1000))),
                new CommandChoice("Après Demain", String.valueOf(System.currentTimeMillis() + (2 * 86400 * 1000))),
                new CommandChoice("J+3", String.valueOf(System.currentTimeMillis() + (3 * 86400 * 1000))),
                new CommandChoice("J+4", String.valueOf(System.currentTimeMillis() + (4 * 86400 * 1000))),
                new CommandChoice("J+5", String.valueOf(System.currentTimeMillis() + (5 * 86400 * 1000))),
                new CommandChoice("J+6", String.valueOf(System.currentTimeMillis() + (6 * 86400 * 1000))),
                new CommandChoice("J+7", String.valueOf(System.currentTimeMillis() + (7 * 86400 * 1000))),
                new CommandChoice("J+14", String.valueOf(System.currentTimeMillis() + (14 * 86400 * 1000))),
                new CommandChoice("J+21", String.valueOf(System.currentTimeMillis() + (21 * 86400 * 1000)))
        );
        this.addRequireArg(OptionType.STRING, "start", "Start timestamp", choices);
        this.addRequireArg(OptionType.STRING, "end", "Faq timestamp", choices);
    }

    @Override
    protected CommandType perform(ZSupport instance) {

        long start = Objects.requireNonNull(this.event.getOption("start")).getAsLong();
        long end = Objects.requireNonNull(this.event.getOption("end")).getAsLong();

        if (start > end) {
            event.reply(":x: La date de fin est inférieur a celle du début !").setEphemeral(true).queue();
            return CommandType.DEFAULT;
        }

        Config.vacation = new Vacation(start, end);

        event.reply(":white_check_mark: Vous venez de commencer vos vacances, profitez bien !").setEphemeral(true).queue();

        instance.getTicketManager().sendVacationInformations(event.getGuild());

        return CommandType.SUCCESS;
    }
}