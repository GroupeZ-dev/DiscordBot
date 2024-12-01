package fr.maxlego08.zsupport.listener;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import fr.maxlego08.zsupport.role.RoleManager;
import fr.maxlego08.zsupport.utils.Constant;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class MemberListener extends ListenerAdapter implements Constant {

	@Override
	public void onMessageReactionAdd(MessageReactionAddEvent event) {

		Guild guild = event.getGuild();
		Role role = guild.getRoleById(ROLE_NOT_ROBOT);
		Member member = event.getMember();
		MessageChannelUnion channel = event.getChannel();

		if (channel.getIdLong() == CHANNEL_ROBOT) {

			guild.addRoleToMember(member, role).queue();

		}

	}

	@Override
	public void onGuildMemberRoleAdd(GuildMemberRoleAddEvent event) {
		Member member = event.getMember();
		List<Role> roles = event.getRoles();
		RoleManager manager = RoleManager.getInstance();
		manager.addRole(member, roles);
	}

	@Override
	public void onGuildMemberRoleRemove(GuildMemberRoleRemoveEvent event) {
		Member member = event.getMember();
		List<Role> roles = event.getRoles();
		RoleManager manager = RoleManager.getInstance();
		manager.removeRole(member, roles);
	}

	@Override
	public void onGuildMemberJoin(GuildMemberJoinEvent event) {

		Guild guild = event.getGuild();
		Role role = guild.getRoleById(ROLE_DEFAULT);
		if (role == null) return;
		Member member = event.getMember();
		guild.addRoleToMember(member, role).queue();

		OffsetDateTime dateTime = member.getUser().getTimeCreated();
		Date date = new Date();
		OffsetDateTime offsetDateTime = date.toInstant().atOffset(ZoneOffset.UTC);

		RoleManager manager = RoleManager.getInstance();
		if (!manager.giveRoles(guild, member, guild.getRoleById(ROLE_NOT_ROBOT)))
			if (Math.abs(offsetDateTime.getYear() - dateTime.getYear()) >= 1) {
				Role roleBot = guild.getRoleById(ROLE_NOT_ROBOT);
				guild.addRoleToMember(member, roleBot).queue();
			}

	}

}
