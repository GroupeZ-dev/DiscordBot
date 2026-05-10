package fr.maxlego08.zsupport.command.commands;

import java.awt.Color;
import java.time.OffsetDateTime;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.command.CommandType;
import fr.maxlego08.zsupport.command.VCommand;
import fr.maxlego08.zsupport.utils.Constant;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

import java.util.Optional;

public class CommandPurchase extends VCommand {

	public CommandPurchase(CommandManager commandManager) {
		super(commandManager);
		this.permission = Permission.ADMINISTRATOR;
		this.description = "Display information about where to buy or download GroupeZ plugins";
	}

	@Override
	protected CommandType perform(ZSupport main) {

		EmbedBuilder builder = new EmbedBuilder();
		builder.setTitle("Where to buy or download GroupeZ plugins?");
		setEmbedFooter(this.guild, builder, new Color(45, 200, 45));

		// Try to find the #build channel to mention it correctly
		TextChannel buildChannel = this.guild.getTextChannelById(1374823204303929384L);
		String buildChannelMention = buildChannel != null ? buildChannel.getAsMention() : "https://discord.com/channels/511516467615760405/1374823204303929384";

		setDescription(builder,
				"You can purchase or download GroupeZ plugins from the following marketplaces:",
				"",
				"- SpigotMC: https://www.spigotmc.org/resources/authors/maxlego08.276655/",
				"- BuiltByBit: https://builtbybit.com/creators/maxlego08.85132/",
				"- GroupeZ marketplace: https://groupez.dev/resources/authors/maxlego08.1",
				"- Modrinth: https://modrinth.com/organization/groupez",
				"- MCModels: https://mcmodels.net/vendors/224/groupez",
				"",
				"You can also access the source code of many GroupeZ plugins on GitHub:",
				"- GitHub: https://github.com/GroupeZ-dev/",
				"",
				"If you need test versions of some plugins, you can get them in " + buildChannelMention + "."
		);

		Emoji emote = this.guild.getEmojiById(Config.groupezEmote);
		Button buttonUrl = new ButtonImpl(
				"btn:url",
				"Open GroupeZ marketplace",
				ButtonStyle.LINK,
				"https://groupez.dev/resources/authors/maxlego08.1",
				false,
				emote
		);

		this.textChannel.sendMessageEmbeds(builder.build())
				.setActionRow(buttonUrl)
				.queue(message -> {
					this.event.deferReply(true)
							.setContent("Command message sent successfully.")
							.queue();
				});

		return CommandType.SUCCESS;
	}
}