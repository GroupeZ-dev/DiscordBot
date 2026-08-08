package fr.maxlego08.zsupport.listener;

import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.mib.MibRoleManager;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Branche la synchronisation des paliers MIB sur le cycle de vie de la guilde
 * (docs/discord-tier-sync.md §7).
 *
 * <p>C'est un listener <b>séparé</b> de {@link MemberListener} à dessein : ce dernier appelle
 * {@code RoleManager.giveRoles} à l'arrivée d'un membre, un tout autre mécanisme (mémorisation des
 * rôles dans {@code roles.json}) qui ne doit surtout pas se mélanger avec l'application d'un palier
 * — MIB est la source de vérité exclusive de zMenuPremium et zMenuPro.</p>
 *
 * @author Maxence
 */
public class MibRoleListener extends ListenerAdapter {

    private final MibRoleManager.Settings settings;
    private final AtomicBoolean bootstrapped = new AtomicBoolean(false);

    public MibRoleListener(MibRoleManager.Settings settings) {
        this.settings = settings;
    }

    /**
     * Démarre la synchronisation quand la guilde est prête.
     *
     * <p>Le garde est indispensable : {@code GuildReadyEvent} est ré-émis à <b>chaque</b> reconnexion
     * de la gateway. Sans lui, chaque coupure ouvrirait un second socket relais, et deux sockets qui
     * portent le même {@code connection_id} se chassent mutuellement en boucle (fermeture 4009), ce
     * que le contrat interdit explicitement.</p>
     */
    @Override
    public void onGuildReady(GuildReadyEvent event) {

        if (event.getGuild().getIdLong() != Config.guildId) return;
        if (!this.bootstrapped.compareAndSet(false, true)) return;

        MibRoleManager manager = MibRoleManager.getInstance();
        manager.configure(this.settings);
        manager.start();
    }

    /**
     * Rattrapage à l'arrivée d'un membre.
     *
     * <p>Le relais ne bufferise rien et ne rejoue rien : un nudge émis pendant que le membre n'était
     * pas sur le serveur a été perdu sèchement. Sans ce rattrapage, un client qui lie son compte puis
     * rejoint la guilde attendrait la prochaine passe de réconciliation.</p>
     */
    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {

        if (event.getGuild().getIdLong() != Config.guildId) return;

        MibRoleManager manager = MibRoleManager.getInstance();
        // Synchronisation désactivée ou mal configurée : on ne tente rien. Interroger MIB sans secret
        // ne produirait qu'un 401 et une ligne d'erreur par arrivée sur le serveur.
        if (!manager.isStarted()) return;

        Member member = event.getMember();
        manager.syncMemberAsync(member.getIdLong());
    }
}
