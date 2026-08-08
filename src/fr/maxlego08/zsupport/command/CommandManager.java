package fr.maxlego08.zsupport.command;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.command.commands.CommandClear;
import fr.maxlego08.zsupport.command.commands.CommandClose;
import fr.maxlego08.zsupport.command.commands.CommandCustomerVerify;
import fr.maxlego08.zsupport.command.commands.CommandDocumentation;
import fr.maxlego08.zsupport.command.commands.CommandLog;
import fr.maxlego08.zsupport.command.commands.CommandMibSync;
import fr.maxlego08.zsupport.command.commands.CommandPauseAutoClose;
import fr.maxlego08.zsupport.command.commands.CommandPlugins;
import fr.maxlego08.zsupport.command.commands.CommandPurchase;
import fr.maxlego08.zsupport.command.commands.CommandRoles;
import fr.maxlego08.zsupport.command.commands.CommandRules;
import fr.maxlego08.zsupport.command.commands.CommandServer;
import fr.maxlego08.zsupport.command.commands.CommandStop;
import fr.maxlego08.zsupport.command.commands.CommandVacation;
import fr.maxlego08.zsupport.command.commands.CommandVerify;
import fr.maxlego08.zsupport.command.commands.tickets.CommandTicketSet;
import fr.maxlego08.zsupport.lang.BasicMessage;
import fr.maxlego08.zsupport.utils.Constant;
import fr.maxlego08.zsupport.utils.ZUtils;
import fr.maxlego08.zsupport.utils.commands.ConsoleSender;
import fr.maxlego08.zsupport.utils.commands.PlayerSender;
import fr.maxlego08.zsupport.utils.commands.Sender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager extends ZUtils implements Constant {

    private final ZSupport support;
    private final List<VCommand> commands = new ArrayList<VCommand>();
    private Guild guild;

    public CommandManager(ZSupport support) {
        super();
        this.support = support;
        registerCommands();
    }

    public void registerCommands(){
        commands.clear();
        registerCommand("stop", new CommandStop(this), "end");
        registerCommand("ticketset", new CommandTicketSet(this));
        registerCommand("server", new CommandServer(this));
        registerCommand("roles", new CommandRoles(this));
        registerCommand("documentation", new CommandDocumentation(this), "d", "doc", "docs");
        registerCommand("clear", new CommandClear(this));
        registerCommand("verify", new CommandVerify(this));
        registerCommand("howtopurchase", new CommandPurchase(this));
        registerCommand("plugins", new CommandPlugins(this));
        registerCommand("rules", new CommandRules(this));
        registerCommand("log", new CommandLog(this));
        registerCommand("setvacation", new CommandVacation(this));

        registerCommand("customer-verify", new CommandCustomerVerify(this));
        registerCommand("close", new CommandClose(this));
        registerCommand("pauseautoclose", new CommandPauseAutoClose(this));
        registerCommand("mib-sync", new CommandMibSync(this));
    }

    public void addCommand(VCommand command) {
        this.commands.add(command);
    }

    public void registerCommand(String cmd, VCommand command, String... strings) {
        command.subCommands.add(cmd);
        command.subCommands.addAll(Arrays.asList(strings));
        this.commands.add(command);
    }

    /*private void registerDiscordCommand(VCommand command) {
        String cmd = command.getSubCommands().get(0);
        System.out.println("Enregistrement de la commande " + cmd + " (" + command.getDescription() + "), après le chargement du bot !");
        guild.updateCommands().addCommands(command.toCommandData()).queue();
    }*/

    /**
     * @param sender
     * @param cmd
     * @param args
     * @param event
     * @return
     */
    public boolean onCommand(Sender sender, String cmd, String[] args, SlashCommandInteractionEvent event) {
        for (VCommand command : this.commands) {
            if (command.getSubCommands().contains(cmd.toLowerCase())) {
                if ((args.length == 0 || command.isIgnoreParent()) && command.getParent() == null) {
                    CommandType type = processRequirements(command, sender, args, event);
                    if (!type.equals(CommandType.CONTINUE)) {
                        return true;
                    }
                }
            } else if (args.length >= 1 && command.getParent() != null
                    && canExecute(args, cmd.toLowerCase(), command)) {
                CommandType type = processRequirements(command, sender, args, event);
                if (!type.equals(CommandType.CONTINUE)) {
                    return true;
                }
            }
        }
        sender.sendEmbed(BasicMessage.COMMAND_NOT_FOUND, true);
        return true;
    }

    /**
     * @param args
     * @param cmd
     * @param command
     * @return true if can execute
     */
    private boolean canExecute(String[] args, String cmd, VCommand command) {
        for (int index = args.length - 1; index > -1; index--) {
            if (command.getSubCommands().contains(args[index].toLowerCase())) {
                if (command.isIgnoreArgs()
                        && (command.getParent() != null ? canExecute(args, cmd, command.getParent(), index - 1) : true))
                    return true;
                if (index < args.length - 1)
                    return false;
                return canExecute(args, cmd, command.getParent(), index - 1);
            }
        }
        return false;
    }

    /**
     * @param args
     * @param cmd
     * @param command
     * @param index
     * @return
     */
    private boolean canExecute(String[] args, String cmd, VCommand command, int index) {
        if (index < 0 && command.getSubCommands().contains(cmd.toLowerCase()))
            return true;
        else if (index < 0)
            return false;
        else if (command.getSubCommands().contains(args[index].toLowerCase()))
            return canExecute(args, cmd, command.getParent(), index - 1);
        else
            return false;
    }

    /**
     * Allows you to run the command
     *
     * @param command
     * @param sender
     * @param strings
     * @return
     */
    private CommandType processRequirements(VCommand command, Sender sender, String[] strings,
                                            SlashCommandInteractionEvent event) {

        if (!(sender instanceof PlayerSender) && !command.isConsoleCanUse()) {
            sender.sendMessage(event, BasicMessage.COMMAND_NO_CONSOLE);
            return CommandType.DEFAULT;
        }
        if (!(sender instanceof ConsoleSender) && !command.isPlayerCanUse()) {
            sender.sendEmbed(event, BasicMessage.COMMAND_NO_PLAYER);
            return CommandType.DEFAULT;
        }
        if (sender instanceof PlayerSender && command.isOnlyInCommandChannel()) {

            MessageChannelUnion channel = event.getChannel();

            if (channel.getIdLong() != Config.commandChannel) {
                EmbedBuilder builder = new EmbedBuilder();
                builder.setColor(Color.RED);

                TextChannel commandChannel = channel.getJDA().getTextChannelById(Config.commandChannel);
                String message = ":x: You must use the " + commandChannel.getAsMention() + " to send a command.";
                builder.setDescription(message);

                event.deferReply(true).addEmbeds(builder.build()).queue();
                return CommandType.DEFAULT;
            }
        }

        if (command.getPermission() == null || sender.hasPermission(command.getPermission())) {
            CommandType returnType = command.prePerform(support, sender, strings, event);

            if (returnType == CommandType.SYNTAX_ERROR) {
                sender.sendMessage(event, BasicMessage.COMMAND_SYNTAX_ERROR, true, command.getSyntaxe());
            }

            return returnType;
        }
        sender.sendEmbed(event, BasicMessage.COMMAND_NO_PERMISSION);
        return CommandType.DEFAULT;
    }

    public List<VCommand> getCommands() {
        return commands;
    }

    public void setGuild(Guild guild) {
        this.guild = guild;
    }
}
