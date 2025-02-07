package fr.maxlego08.zsupport.command.commands.tickets;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.command.CommandType;
import fr.maxlego08.zsupport.command.VCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

import java.awt.*;

public class CommandTicketSet extends VCommand {

    public CommandTicketSet(CommandManager commandManager) {
        super(commandManager);
        this.consoleCanUse = false;
        this.permission = Permission.ADMINISTRATOR;
        this.description = "Afficher le message des tickets";
    }

    @Override
    protected CommandType perform(ZSupport main) {

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("🎫 Create a ticket");
        setEmbedFooter(this.guild, builder);

        setDescription(builder,
                "",
                "**Ticket Creation Information**",
                "📌 Click the button below to create a support ticket.",
                "⚠️ **Important:** Ensure that your Discord account is linked to your GroupeZ account before opening a ticket.",
                "🔗 Link your account here: [GroupeZ Dashboard](https://groupez.dev/dashboard/account)",
                "",
                "**Rules & Guidelines**",
                "1️⃣ You must follow the Discord server https://discord.com/channels/511516467615760405/765577829122572318 while using the support system.",
                "2️⃣ Please follow all instructions given by the bot carefully.",
                "3️⃣ Provide as much information as possible regarding your issue to receive faster assistance.",
                "4️⃣ Do **not** mention staff members; they will assist you as soon as possible.",
                "5️⃣ The GroupeZ support team is in the Paris timezone (UTC+1).",
                "6️⃣ **Official support hours:** Support is available every week-day from **10:00 AM to 5:00 PM (UTC+1).**",
                "7️⃣ Response times may vary; please allow **24 to 48 hours** for a reply from a support team member.",
                "8️⃣ If you are inactive in the ticket for **72 hours**, it will be automatically closed.",
                "",
                "**Data Collection & Consent**",
                "📜 By opening a ticket, you acknowledge and agree that GroupeZ will collect and store messages exchanged in the ticket for record-keeping purposes."
        );

        builder.setImage("https://img.groupez.dev/groupez/link-discord.gif");

        Button buttonZMenu = new ButtonImpl(BUTTON_ZMENU, "Create a ticket for zMenu", ButtonStyle.SECONDARY, false, guild.getEmojiById(Config.zMenuEmote));
        Button buttonFr = new ButtonImpl(BUTTON_FR, "Créer un ticket en Français", ButtonStyle.PRIMARY, false, Emoji.fromUnicode("U+1F1EB U+1F1F7"));
        Button buttonEn = new ButtonImpl(BUTTON_EN, "Create a ticket in English", ButtonStyle.SUCCESS, false, Emoji.fromUnicode("U+1F1FA U+1F1F8"));

        this.textChannel.sendMessageEmbeds(builder.build()).setActionRow(buttonZMenu, buttonFr, buttonEn).queue(message -> {
            this.event.deferReply(true).setContent("Envoie de la commande effectué avec succès.").queue();
        });

        return CommandType.SUCCESS;
    }

}
