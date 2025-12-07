package fr.maxlego08.zsupport.listener;

import fr.maxlego08.zsupport.role.RoleManager;
import fr.maxlego08.zsupport.utils.Constant;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleAddEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRoleRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.List;

public class MemberListener extends ListenerAdapter implements Constant {

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
        Member member = event.getMember();

        RoleManager manager = RoleManager.getInstance();
        manager.giveRoles(guild, member);
    }
}
