package fr.maxlego08.zsupport.command.commands;

import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.command.CommandType;
import fr.maxlego08.zsupport.command.VCommand;
import net.dv8tion.jda.api.EmbedBuilder;

public class CommandServer extends VCommand {

    public CommandServer(CommandManager commandManager) {
        super(commandManager);
        this.consoleCanUse = false;
        this.onlyInCommandChannel = true;
        this.description = "Display server information";
    }

    @Override
    protected CommandType perform(ZSupport main) {

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Server Info");
        setEmbedFooter(this.guild, builder);

        builder.addField("Name of discord", guild.getName(), true);
        builder.addField("Founder of discord", guild.getMemberById(522359210844094479L).getAsMention(), true);
        builder.addField("Creation date", guild.getTimeCreated().toString(), false);
        builder.addField("Number of categories", String.valueOf(guild.getCategories().size()), true);
        builder.addField("Number of text channel", String.valueOf(guild.getTextChannels().size()), true);
        builder.addField("Number of voice channel", String.valueOf(guild.getVoiceChannels().size()), true);
        builder.addField("Membres", String.valueOf(guild.getMemberCount()), false);

        builder.addField("SIRET", "887 617 496 00013", true);
        builder.addField("E-mail", "contact@groupez.dev", true);

        this.event.replyEmbeds(builder.build()).setEphemeral(true).queue();


        return CommandType.SUCCESS;
    }
}
