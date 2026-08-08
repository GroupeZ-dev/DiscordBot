package fr.maxlego08.zsupport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import fr.maxlego08.zsupport.command.CommandManager;
import fr.maxlego08.zsupport.ai.AIManager;
import fr.maxlego08.zsupport.faq.FaqManager;
import fr.maxlego08.zsupport.listener.CommandListener;
import fr.maxlego08.zsupport.listener.MemberListener;
import fr.maxlego08.zsupport.listener.MibRoleListener;
import fr.maxlego08.zsupport.mib.MibRoleManager;
import fr.maxlego08.zsupport.role.RoleManager;
import fr.maxlego08.zsupport.tickets.TicketListener;
import fr.maxlego08.zsupport.tickets.TicketManager;
import fr.maxlego08.zsupport.utils.Constant;
import fr.maxlego08.zsupport.utils.storage.Persist;
import fr.maxlego08.zsupport.utils.storage.Savable;
import gs.mclo.api.MclogsClient;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import javax.security.auth.login.LoginException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ZSupport implements Constant {

    public static ZSupport instance;
    private final Gson gson;
    private final JDA jda;
    private final CommandManager commandManager;
    private final CommandListener commandListener;
    private final TicketManager ticketManager;
    private final List<Savable> savables = new ArrayList<>();
    private final Persist persist;
    private final MemberListener memberListener;
    // private final XpListener xpListener;
    private final MclogsClient mclogsClient;
    private final FaqManager faqManager;
    private final AIManager aiManager;
    private final MibRoleManager mibRoleManager;
    private final MibRoleListener mibRoleListener;

    public ZSupport() throws LoginException {

        instance = this;

        this.mclogsClient = new MclogsClient("GroupeZ-Bot", VERSION);
        this.gson = getGsonBuilder().create();
        this.persist = new Persist(this);

        this.savables.add(Config.getInstance());
        this.savables.add(RoleManager.getInstance());

        this.savables.forEach(save -> save.load(this.persist));

        this.ticketManager = new TicketManager(this);
        this.commandManager = new CommandManager(this);
        this.commandListener = new CommandListener(this);
        this.memberListener = new MemberListener();
        this.faqManager = new FaqManager(this.ticketManager.getSqlManager());
        this.aiManager = new AIManager();
        // xpListener = new XpListener(this);

        // La configuration est construite ici, une fois Config relu depuis config.json, mais le
        // démarrage effectif attend le GuildReadyEvent : la Guild n'existe pas encore à cet instant et
        // la réconciliation lit les porteurs de rôles dans le cache JDA.
        this.mibRoleManager = MibRoleManager.getInstance();
        this.mibRoleListener = new MibRoleListener(createMibSettings());

        Thread thread = new Thread(this.commandListener, "bot");
        thread.start();

        System.out.println(PREFIX_CONSOLE + "Chargement du bot");

        List<GatewayIntent> list = new ArrayList<>();

        list.add(GatewayIntent.GUILD_MEMBERS);
        list.add(GatewayIntent.GUILD_EMOJIS_AND_STICKERS);
        list.add(GatewayIntent.DIRECT_MESSAGES);
        list.add(GatewayIntent.GUILD_MESSAGE_REACTIONS);
        list.add(GatewayIntent.GUILD_MESSAGES);
        list.add(GatewayIntent.GUILD_MESSAGE_TYPING);
        list.add(GatewayIntent.MESSAGE_CONTENT);

        JDABuilder builder = JDABuilder.create(Config.botToken, list);
        builder.setMemberCachePolicy(MemberCachePolicy.ALL);
        this.jda = builder.build();
        this.jda.getPresence().setActivity(Activity.playing("/help V" + VERSION));
        this.jda.addEventListener(this.commandListener);
        this.jda.addEventListener(this.memberListener);
        this.jda.addEventListener(this.mibRoleListener);
        this.jda.addEventListener(new TicketListener(this.ticketManager));

        /**
         *
         * JDA jda= this.textChannel.getJDA(); Emoji emoji =
         * Emoji.fromEmote(jda.getEmoteById(941374457312850011l));
         * .setActionRow(new ButtonImpl("test", "test", ButtonStyle.SECONDARY,
         * false, emoji))
         *
         */

        /*
         * CommandListUpdateAction action = jda.updateCommands();
         *
         * action.addCommands(new CommandData("hello", "test"));
         * action.addCommands(new CommandData("info", "test"));
         *
         * action.queue();
         */

        System.out.println(PREFIX_CONSOLE + "Bot lancé avec succès !");

        this.ticketManager.load(this.faqManager::selectFqa);

        Timer timer = new Timer();
        long period = 1000 * 60 * 30;
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                savables.forEach(save -> save.save(persist));
            }
        }, period, period);
    }

    public static void main(String[] args) {
        try {
            new ZSupport();
        } catch (LoginException e) {
            e.printStackTrace();
        }
    }

    /**
     * Construit la configuration de la synchronisation des paliers MIB.
     *
     * <p>Les deux secrets sont lus <b>en priorité depuis l'environnement</b> : {@code Config.java} est
     * suivi par git, ses valeurs par défaut compilées doivent rester vides (docs/discord-tier-sync.md
     * §8). Ils sont volontairement distincts : compromettre le relais ne doit pas ouvrir l'oracle
     * « ce compte Discord a-t-il un palier ».</p>
     */
    private MibRoleManager.Settings createMibSettings() {

        MibRoleManager.Settings settings = new MibRoleManager.Settings();

        settings.enabled = Config.mibRoleSyncEnabled;
        settings.dryRun = Config.mibDryRun;
        settings.wsUrl = Config.mibRelayUrl;
        settings.relayToken = resolveMibSecret("MIB_RELAY_TOKEN", Config.mibRelayToken, "jeton du relais");
        settings.apiBaseUrl = Config.mibApiBaseUrl;
        settings.apiSecret = resolveMibSecret("MIB_API_SECRET", Config.mibApiSecret, "secret d'API");
        settings.premiumRoleId = Config.zMenuPremium;
        settings.proRoleId = Config.zMenuPro;
        settings.reconcileIntervalMinutes = Config.mibReconcileIntervalMinutes;
        settings.maxRevocations = Config.mibMaxRevocations;
        settings.guildId = Config.guildId;

        return settings;
    }

    /**
     * Résout un secret, l'environnement d'abord.
     *
     * <p>Seule la <b>source</b> retenue est journalisée, jamais la valeur : la console du bot est
     * relue et copiée par le support.</p>
     */
    private String resolveMibSecret(String variable, String fallback, String label) {

        String environment = System.getenv(variable);
        if (environment != null && !environment.isBlank()) {
            System.out.println(PREFIX_CONSOLE + "[MIB] " + label + " lu depuis l'environnement (" + variable + ")");
            return environment.trim();
        }

        if (fallback != null && !fallback.isBlank()) {
            System.out.println(PREFIX_CONSOLE + "[MIB] " + label + " lu depuis config.json");
            return fallback;
        }

        System.out.println(PREFIX_CONSOLE + "[MIB] " + label + " absent (" + variable
                + " et config.json vides) : la synchronisation des paliers restera désactivée");
        return "";
    }

    public GsonBuilder getGsonBuilder() {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls()
                .excludeFieldsWithModifiers(Modifier.TRANSIENT, Modifier.VOLATILE);
    }

    public Gson getGson() {
        return gson;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }

    public JDA getJda() {
        return jda;
    }

    public void onDisable() {
        this.savables.forEach(save -> {
            System.out.println("Sauvegarde de " + save.getClass().getName());
            save.save(persist);
        });
        this.ticketManager.save();
        // Avant JDA : le relais et l'ordonnanceur dédié doivent être fermés tant que les RestAction en
        // file peuvent encore partir, sinon un retrait de rôle déjà planifié serait perdu.
        this.mibRoleManager.shutdown();
        System.out.println("Shutdown de JDA");
        jda.shutdownNow();
    }

    public CommandListener getCommandListener() {
        return commandListener;
    }

    public TicketManager getTicketManager() {
        return ticketManager;
    }

    public MclogsClient getMclogsClient() {
        return mclogsClient;
    }

    public FaqManager getFaqManager() {
        return faqManager;
    }

    public AIManager getAiManager() {
        return aiManager;
    }

    public MibRoleManager getMibRoleManager() {
        return mibRoleManager;
    }
}
