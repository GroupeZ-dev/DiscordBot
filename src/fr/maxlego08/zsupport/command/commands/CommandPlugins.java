package fr.maxlego08.zsupport.command.commands;

import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.command.CommandType;
import fr.maxlego08.zsupport.command.VCommand;
import fr.maxlego08.zsupport.plugins.PluginManager;
import net.dv8tion.jda.api.Permission;

public class CommandPlugins extends VCommand {

	public CommandPlugins(CommandManager commandManager) {
		super(commandManager);
		this.permission = Permission.ADMINISTRATOR;
		this.description = "Display the list of plugins";
	}

	@Override
	protected CommandType perform(ZSupport main) {

		PluginManager manager = new PluginManager();
		manager.displayPlugins(this.guild, this.textChannel);
		this.event.deferReply(true).setContent("Envoi de la commande effectuée avec succès.").queue();

		return CommandType.SUCCESS;
	}

}
