package fr.maxlego08.zsupport.role;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.utils.storage.Persist;
import fr.maxlego08.zsupport.utils.storage.Savable;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoleManager implements Savable {

    private static final Map<Long, RoleUser> roles = new HashMap<Long, RoleUser>();

    private static volatile RoleManager instance;

    private RoleManager() {
    }

    public static RoleManager getInstance() {
        // Double lock for thread safety.
        if (instance == null) {
            synchronized (RoleManager.class) {
                if (instance == null) {
                    instance = new RoleManager();
                }
            }
        }
        return instance;
    }

    @Override
    public void save(Persist persist) {
        persist.save(this, "roles");
    }

    @Override
    public void load(Persist persist) {
        persist.loadOrSaveDefault(this, RoleManager.class, "roles");
    }

    public RoleUser getRole(long id) {
        if (!roles.containsKey(id)) {
            RoleUser roleUser = new RoleUser(id);
            roles.put(id, roleUser);
            return roleUser;
        }
        return roles.get(id);
    }

    public boolean contains(long id) {
        return roles.containsKey(id);
    }

    public boolean giveRoles(Guild guild, Member member, Role role) {

        if (!this.contains(member.getIdLong())) return false;

        RoleUser roleUser = getRole(member.getIdLong());
        for (long currentRole : roleUser.getRoles()) {
            try {

                Role tmpRole = guild.getRoleById(currentRole);
                guild.addRoleToMember(member, tmpRole).complete();

            } catch (Exception e) {
                return false;
            }
        }

        guild.addRoleToMember(member, role).complete();
        return true;
    }

    public boolean haveRole(Role role) {
        return Config.plugins.stream().noneMatch(plugin -> plugin.getRole() == role.getIdLong());
    }

    public void addRole(Member member, List<Role> addRoles) {

        for (Role role : addRoles) {

            if (haveRole(role)) continue;

            RoleUser roleUser = getRole(member.getIdLong());
            roleUser.add(role.getIdLong());
        }
    }

    public void removeRole(Member member, List<Role> addRoles) {

        for (Role role : addRoles) {

            if (haveRole(role)) continue;

            RoleUser roleUser = getRole(member.getIdLong());
            roleUser.remove(role.getIdLong());
        }
    }
}