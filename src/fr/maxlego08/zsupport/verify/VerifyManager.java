package fr.maxlego08.zsupport.verify;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.mysql.cj.xdevapi.JsonParser;
import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.lang.BasicMessage;
import fr.maxlego08.zsupport.mib.MibRoleManager;
import fr.maxlego08.zsupport.tickets.storage.SqlManager;
import fr.maxlego08.zsupport.utils.Constant;
import fr.maxlego08.zsupport.utils.Plugin;
import fr.maxlego08.zsupport.utils.ZUtils;
import fr.maxlego08.zsupport.utils.commands.PlayerSender;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Class to optimize to avoid making the same request several times
 *
 * @author Maxence
 */
public class VerifyManager extends ZUtils {

    /**
     * static Singleton instance.
     */
    private static volatile VerifyManager instance;
    private final String USER__AGENT = "Mozilla/5.0";
    private final Map<Long, GUser> users = new HashMap<>();

    /**
     * Private constructor for singleton.
     */
    private VerifyManager() {
    }

    /**
     * Return a singleton instance of VerifyManager.
     */
    public static VerifyManager getInstance() {
        // Double lock for thread safety.
        if (instance == null) {
            synchronized (VerifyManager.class) {
                if (instance == null) {
                    instance = new VerifyManager();
                }
            }
        }
        return instance;
    }

    public void submitData(User user, TextChannel textChannel, PlayerSender sender, boolean delete, SlashCommandInteractionEvent event) {
        this.submitData(user, textChannel, sender, delete, null, event);
    }

    /**
     * Allows you to verify the user
     *
     * @param user
     * @param textChannel
     * @param sender
     * @param delete
     */
    public void submitData(User user, TextChannel textChannel, PlayerSender sender, boolean delete, Consumer<GUser> consumer, SlashCommandInteractionEvent event) {
        new Thread(() -> this.sendData(user, textChannel, sender, delete, consumer, event)).start();
    }

    /**
     * Get user informations
     *
     * @param userId
     * @param consumer
     */
    public void getGUser(long userId, Consumer<GUser> consumer) {

        GUser gUser = this.users.getOrDefault(userId, null);
        if (gUser == null || gUser.isExpired()) {
            this.getUserInformation(userId, consumer);
        } else {
            consumer.accept(gUser);
        }

    }

    /**
     * Get user information from website
     *
     * @param userId
     * @param consumer
     */
    @SuppressWarnings("unchecked")
    private void getUserInformation(long userId, Consumer<GUser> consumer) {

        Thread thread = new Thread(() -> {

            try {
                String urlAsString = String.format(Config.API_URL, userId);
                URL url = new URL(urlAsString);

                HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

                // add reuqest header
                con.setRequestMethod("POST");
                con.setRequestProperty("User-Agent", this.USER__AGENT);
                con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

                // Send post request
                con.setDoOutput(true);
                DataOutputStream wr = new DataOutputStream(con.getOutputStream());
                wr.flush();
                wr.close();

                int responseCode = con.getResponseCode();

                if (responseCode != 200) {
                    return;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuffer response = new StringBuffer();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                Gson gson = ZSupport.instance.getGson();

                Type listType = new TypeToken<Map<String, Object>>() {
                }.getType();

                Map<String, Object> values = gson.fromJson(response.toString(), listType);

                Map<String, Object> userAsMap = (Map<String, Object>) values.get("user");
                GUser gUser = new GUser((String) userAsMap.get("name"), ((Number) userAsMap.get("id")).intValue(), (String) userAsMap.get("avatar"), new ArrayList<>());

                this.users.put(userId, gUser);
                consumer.accept(gUser);
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
        thread.start();

    }

    public void userIsLink(User user, Consumer<UserData> consumerSuccess, Runnable runnableError) {
        try {
            String url = String.format(Config.API_URL, user.getIdLong());
            URL obj = new URL(url);
            HttpsURLConnection con = (HttpsURLConnection) obj.openConnection();

            // add request header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", this.USER__AGENT);
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            // Send post-request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();

            if (responseCode != 200) {
                runnableError.run();
                return;
            }

            // Read response
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            Map<String, Object> values = ZSupport.instance.getGson().fromJson(response.toString(), new TypeToken<Map<String, Object>>() {
            }.getType());

            Map<String, Object> map = (Map<String, Object>) values.get("user");
            long id = ((Number)map.get("id")).longValue();
            String name = map.get("name").toString();
            String avatar = map.get("avatar").toString();
            UserData linkedUser = new UserData(id, name, avatar);

            // Pass the User object to the consumer
            consumerSuccess.accept(linkedUser);
        } catch (IOException e) {
            runnableError.run();
        }
    }


    public record UserData(long id, String name, String avatar){

    }

    public void hasPurchasePlugin(User user, Plugin plugin, Consumer<Boolean> consumer) {
        SqlManager.service.execute(() -> {
            try {

                URL url = new URL(String.format(Config.API_URL, user.getIdLong()));

                HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();

                // add request header
                httpsURLConnection.setRequestMethod("POST");
                httpsURLConnection.setRequestProperty("User-Agent", this.USER__AGENT);
                httpsURLConnection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

                // Send post-request
                httpsURLConnection.setDoOutput(true);
                DataOutputStream dataOutputStream = new DataOutputStream(httpsURLConnection.getOutputStream());
                dataOutputStream.flush();
                dataOutputStream.close();

                int responseCode = httpsURLConnection.getResponseCode();

                if (responseCode != 200) {
                    consumer.accept(false);
                    return;
                }

                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = bufferedReader.readLine()) != null) {
                    response.append(inputLine);
                }
                bufferedReader.close();

                Gson gson = ZSupport.instance.getGson();

                Map<String, Object> values = gson.fromJson(response.toString(), new TypeToken<Map<String, Object>>() {
                }.getType());

                List<GPlugin> pluginList = GPlugin.toList((List<Object>) values.get("plugins"));
                consumer.accept(pluginList.stream().anyMatch(gPlugin -> gPlugin.getPluginId() == plugin.getPluginId()));

            } catch (Exception exception) {
                exception.printStackTrace();
                consumer.accept(false);
            }
        });
    }

    private void sendData(User user, TextChannel textChannel, PlayerSender sender, boolean delete, Consumer<GUser> consumer, SlashCommandInteractionEvent event) {

        try {

            Guild guild = textChannel.getGuild();

            String urlAsString = String.format(Config.API_URL, user.getIdLong());
            URL url = new URL(urlAsString);

            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

            // add reuqest header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", this.USER__AGENT);
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();

            // If the query returned an error
            if (responseCode != 200) {
                this.sendErrorMessage(event, textChannel, user, guild, BasicMessage.VERIFY_ERROR, null);
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            Gson gson = ZSupport.instance.getGson();

            Type listType = new TypeToken<Map<String, Object>>() {
            }.getType();

            Map<String, Object> values = gson.fromJson(response.toString(), listType);

            Map<String, Object> userAsMap = (Map<String, Object>) values.get("user");
            List<GPlugin> list = GPlugin.toList((List<Object>) values.get("plugins"));

            GUser gUser = new GUser((String) userAsMap.get("name"), ((Number) userAsMap.get("id")).intValue(), (String) userAsMap.get("avatar"), list);

            this.users.put(user.getIdLong(), gUser);

            // If the user has not purchased any plugin

            if (list.size() == 0) {
                if (consumer != null) {
                    consumer.accept(gUser);
                } else {
                    this.sendErrorMessage(event, textChannel, user, guild, BasicMessage.VERIFY_ERROR_EMPTY, gUser);
                }
                return;
            }

            Member member = sender.getMember();

            List<Role> roles = list.stream().map(e -> {
                Optional<Plugin> optional = Config.getPlugin(e.getPluginId());
                if (optional.isPresent()) {
                    Plugin plugin = optional.get();
                    Role role = guild.getRoleById(plugin.getRole());
                    if (hasRole(member, plugin.getRole())) {
                        return null;
                    }
                    return role;
                }
                return null;
            }).filter(e -> e != null).collect(Collectors.toList());

            // If the user already has all the roles
            if (roles.size() == 0) {
                if (consumer != null) {
                    consumer.accept(gUser);
                } else {
                    this.sendErrorMessage(event, textChannel, user, guild, BasicMessage.VERIFY_ERROR_ALREADY, gUser);
                }
                return;
            }

            roles.forEach(role -> {
                guild.addRoleToMember(member, role).queue();
                System.out.println("Ajout du r§le " + roles.get(0).getName() + " § l'utilisateur " + user.getName());
            });

            if (consumer != null) {

                consumer.accept(gUser);

            } else {

                Random random = new Random();
                EmbedBuilder builder = new EmbedBuilder();

                builder.setTitle("GroupeZ - " + user.getName(), gUser.getDashboardURL());
                builder.setColor(Color.getHSBColor(random.nextInt(255), random.nextInt(255), random.nextInt(255)));
                builder.setFooter(Constant.YEAR + " - " + guild.getName(), guild.getIconUrl());
                builder.setThumbnail(gUser.getAvatar());

                String footer = "\n\n\nUse ``/verify`` to verify your account.";
                if (textChannel.getIdLong() != Config.commandChannel) {
                    TextChannel commandChannel = guild.getTextChannelById(Config.commandChannel);
                    footer += "\nOn " + commandChannel.getAsMention();
                }

                if (roles.size() == 1) {
                    builder.setDescription("You just got the role: " + roles.get(0).getAsMention() + footer);
                } else {
                    String str = toList(roles.stream().map(e -> e.getAsMention()).collect(Collectors.toList()));
                    builder.setDescription("You just got the roles: " + str + footer);
                }

                textChannel.sendMessageEmbeds(builder.build()).queue();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Interroge Minecraft Inventory Builder sur le droit d'ouvrir un ticket zMenu.
     *
     * <p><b>Bug de production corrigé ici.</b> Cette méthode appelait
     * {@code /api/v1/discord/user/{id}} <b>sans aucun en-tête d'authentification</b> alors que la
     * route exige un token Sanctum : elle recevait donc 401 à chaque appel. Le repli
     * {@code MIB(false, 0)} était interprété comme un refus et <b>chaque ticket zMenu partait en
     * fermeture automatique</b>, salon supprimé une minute plus tard, clients payants compris. Elle
     * lisait par ailleurs un champ {@code power} que MIB ne renvoie plus.</p>
     *
     * <p>On passe donc par {@code GET /api/v2/discord/tiers/{id}} (docs/discord-tier-sync.md §4.4),
     * derrière le secret dédié {@code X-Bot-Secret}. Toute panne — timeout, 401, 5xx, corps illisible,
     * secret non renseigné — produit {@link MIB#technicalFailure()} et <b>jamais</b> un refus : c'est
     * l'appelant qui route vers la validation humaine.</p>
     *
     * <p>L'appel est asynchrone et sort du thread JDA, contrairement à l'ancien
     * {@code HttpClient.send} bloquant qui gelait le callback dans lequel il était invoqué.</p>
     *
     * @param textChannel conservé pour la compatibilité des appelants, inutilisé
     */
    public void verifyMinecraftInventoryUser(User user, TextChannel textChannel, Consumer<MIB> consumer) {

        MibRoleManager.getInstance().fetchSingleAsync(user.getIdLong(),
                single -> consumer.accept(single.canOpen() ? MIB.granted(single.tier()) : MIB.denied()),
                throwable -> {
                    System.err.println("[MIB] Vérification zMenu impossible pour " + user.getId()
                            + ", bascule en validation humaine : " + throwable);
                    consumer.accept(MIB.technicalFailure());
                });
    }

    private void sendErrorMessage(SlashCommandInteractionEvent event, TextChannel textChannel, User user, Guild guild, BasicMessage basicMessage, GUser gUser) {
        EmbedBuilder builder = new EmbedBuilder();

        builder.setColor(new Color(240, 10, 10));
        builder.setFooter(Constant.YEAR + " - " + guild.getName(), guild.getIconUrl());

        String desc = basicMessage.getMessage();

        if (textChannel.getIdLong() != Config.commandChannel) {
            TextChannel commandChannel = guild.getTextChannelById(Config.commandChannel);
            desc = desc.replace("%channel%", "on " + commandChannel.getAsMention());
        }
        desc = desc.replace(" %channel%", "");

        builder.setDescription(desc);

        if (gUser != null) {
            builder.setTitle("GroupeZ - " + user.getName(), gUser.getDashboardURL());
            builder.setThumbnail(gUser.getAvatar());
        } else {
            builder.setTitle("GroupeZ - " + user.getName());
        }

        textChannel.sendMessageEmbeds(builder.build()).queue();
    }

    public void updateUserAsync(IReplyCallback event, Guild guild, Member targetUser, long pluginId, Message message) {
        SqlManager.service.execute(() -> this.updateUser(event, targetUser, pluginId, guild, message));
    }

    private void updateUser(IReplyCallback event, Member targetUser, long pluginId, Guild guild, Message message) {
        try {

            String urlAsString = String.format(Config.API_URL_VERIFY_CUSTOMER, targetUser.getIdLong(), pluginId);

            URL url = new URL(urlAsString);

            String jsonPayload = "{\"custom_password\": \"" + Config.CUSTOM_KEY + "\"}";

            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

            // add reuqest header
            con.setRequestMethod("POST");
            con.setRequestProperty("User-Agent", this.USER__AGENT);
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            con.setRequestProperty("Content-Type", "application/json");

            // Send post-request
            con.setDoOutput(true);

            OutputStream os = con.getOutputStream();
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);

            DataOutputStream wr = new DataOutputStream(os);

            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();

            // If the query returned an error
            if (responseCode != 200) {
                if (event == null) {
                    message.reply("Erreur, impossible de faire ceci. (Client ou plugin introuvable)").queue();
                } else
                    event.reply("Erreur, impossible de faire ceci. (Client ou plugin introuvable)").setEphemeral(true).queue();
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));

            String inputLine;
            StringBuffer response = new StringBuffer();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            Gson gson = ZSupport.instance.getGson();

            Type listType = new TypeToken<Map<String, Object>>() {
            }.getType();

            Map<String, Object> values = gson.fromJson(response.toString(), listType);

            String result = (String) values.get("message");
            if (result.equals("ok")) {
                Optional<Plugin> optional = Config.getPlugin((int) pluginId);
                if (optional.isPresent()) {
                    Plugin plugin = optional.get();
                    Role role = guild.getRoleById(plugin.getRole());

                    if (role == null) {
                        if (event == null) {
                            message.reply("Could’t find the rank").queue();
                        } else event.reply("Could’t find the rank").setEphemeral(true).queue();
                        return;
                    }

                    String messageReply = ":white_check_mark: Confirmation of the plugin purchase **" + plugin.getName() + "** by the customer " + targetUser.getAsMention() + ", addition of the role " + role.getAsMention() + ".";
                    if (event == null) {
                        message.reply(messageReply).queue();
                    } else event.reply(messageReply).queue();

                    if (!hasRole(targetUser, plugin.getRole())) {
                        guild.addRoleToMember(targetUser, role).queue();
                        System.out.println("Ajout du rôle " + role.getName() + " à l'utilisateur " + targetUser.getUser().getName());
                    }
                } else {
                    if (event == null) {
                        message.reply("Unable to find plugin with ID " + pluginId).queue();
                    } else event.reply("Unable to find plugin with ID " + pluginId).setEphemeral(true).queue();
                }

            } else {
                if (event == null) {
                    message.reply("Unable to add the plugin, the client must already have it.").queue();
                } else {
                    event.reply("Unable to add the plugin, the client must already have it.").setEphemeral(true).queue();
                }
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

}
