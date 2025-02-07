package fr.maxlego08.zsupport.command.commands;

import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.command.CommandType;
import fr.maxlego08.zsupport.command.VCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CommandRules extends VCommand {

    public CommandRules(CommandManager commandManager) {
        super(commandManager);
        this.consoleCanUse = false;
        this.permission = Permission.ADMINISTRATOR;
        this.description = "Display the rules of discord";
    }

    @Override
    protected CommandType perform(ZSupport main) {

        sendRules();
        sendFreeInfo();
        sendInfo();

        return CommandType.SUCCESS;
    }

    private void sendFreeInfo() {

        EmbedBuilder builder = new EmbedBuilder();

        builder.setColor(new Color(40, 221, 237));
        builder.setDescription(
                "**FREE PLUGIN**: The support of free plugins is done only in the dedicated channels. It's a community support, if the staff doesn't answer you, just wait. Don't hesitate to ask your question again or to look if someone already asked this question.");

        this.textChannel.sendMessageEmbeds(builder.build()).queue();

    }

    private void sendInfo() {

        EmbedBuilder builder = new EmbedBuilder();

        builder.setColor(new Color(51, 232, 6));
        builder.setDescription("**PLEASE NOTE**: If you do not respect the rules of the discord you risk sanctions that can go from a simple mute to a permanent banishment from the discord.");

        this.textChannel.sendMessageEmbeds(builder.build()).queue();

    }

    private void sendRules() {
        List<String> rules = new ArrayList<>();
        rules.add("```ansi\n" +
                "It is \u001B[2;31mforbidden\u001B[0m to \u001B[1;2mmention\u001B[0m a staff member (Remember to disable mentions when replying to a message).\n" +
                "```");
        rules.add("```ansi\n" +
				"You can't get support for a paid plugin if you didn't buy it. You have to \u001B[2;31mshow a proof of purchase\u001B[0m.\n" +
				"```");
        rules.add("```ansi\n" +
				"It is forbidden to go to the buyers of the plugins to ask them for help.\n" +
				"```");
        rules.add(
                "```ansi\n" +
						"If you \u001B[2;31mthreaten\u001B[0m to put up a bad review for any reason, you will be \u001B[1;2mbanned\u001B[0m directly. Any threat because you are unhappy will result in the same punishment. You must respect the discord team.\n" +
						"```");
        rules.add("```ansi\n" +
				"Do not insult a player(s) in public.\n" +
				"```");
        rules.add("```ansi\n" +
				"Do not advertise your discords and servers.\n" +
				"```");
        rules.add("```ansi\n" +
				"Do not post inappropriate content.\n" +
				"```");
        rules.add("```ansi\n" +
				"Do not capitalize phrases.\n" +
				"```");
        rules.add("```ansi\n" +
				"Don't \u001B[1;2mspam/flood\u001B[0m to keep the chat clean.\n" +
				"```");
        rules.add("```ansi\n" +
				"Do not post private/IRL things without the permission of the person concerned.\n" +
				"```");
        rules.add("```ansi\n" +
				"Don't impersonate someone else.\n" +
				"```");
        rules.add("```ansi\n" +
				"Not to have a nickname that contains inappropriate comments.\n" +
				"```");
        rules.add("```ansi\n" +
				"Do not have a pornographic character in your nickname.\n" +
				"```");
        rules.add("```ansi\n" +
				"For any information or rank request for a plugin please create a ticket.\n" +
				"```");

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Discord Rules");
        setEmbedFooter(this.guild, builder, new Color(216, 15, 106));

        for (int a = 0; a != rules.size(); a++) {
            int index = a + 1;
            builder.addField("`Article " + index + "`", rules.get(a), false);
        }

        this.textChannel.sendMessageEmbeds(builder.build()).queue();
    }

}
