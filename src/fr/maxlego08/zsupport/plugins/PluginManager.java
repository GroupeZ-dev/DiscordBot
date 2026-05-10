package fr.maxlego08.zsupport.plugins;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.utils.Constant;
import fr.maxlego08.zsupport.utils.Plugin;
import fr.maxlego08.zsupport.utils.ZUtils;
import fr.maxlego08.zsupport.utils.image.ImageHelper;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.internal.interactions.component.ButtonImpl;

import javax.net.ssl.HttpsURLConnection;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class PluginManager extends ZUtils implements Constant {

    private static final ExecutorService executor = Executors.newFixedThreadPool(1);

    public static void fetchResource(Plugin plugin, Consumer<Resource> consumer) {

        executor.execute(() -> {

            try {

                String urlAsString = String.format(Config.API_RESOURCE_URL, plugin.getPluginId());
                URL url = new URL(urlAsString);

                HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

                con.setRequestMethod("GET");
                con.setRequestProperty("User-Agent", "Mozilla/5.0");
                con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

                int responseCode = con.getResponseCode();

                if (responseCode != 200) {
                    System.out.println("Erreur : " + responseCode);
                    return;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                Gson gson = ZSupport.instance.getGson();

                Type resourceType = new TypeToken<Resource>() {
                }.getType();

                Resource resource = gson.fromJson(response.toString(), resourceType);
                consumer.accept(resource);

            } catch (Exception e) {
                e.printStackTrace();
            }

        });

    }

    public void displayPlugins(Guild guild, MessageChannel channel) {
        Thread thread = new Thread(() -> Config.plugins.forEach(plugin -> this.displayPlugin(guild, plugin, channel)));
        thread.start();
    }

    public void displayPlugin(Guild guild, Plugin plugin, MessageChannel channel) {

        try {

            String urlAsString = String.format(Config.API_RESOURCE_URL, plugin.getPluginId());
            URL url = URI.create(urlAsString).toURL();

            HttpsURLConnection con = (HttpsURLConnection) url.openConnection();

            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            int responseCode = con.getResponseCode();

            if (responseCode != 200) {
                System.out.println("Erreur : " + responseCode);
                return;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            Gson gson = ZSupport.instance.getGson();

            Type resourceType = new TypeToken<Resource>() {
            }.getType();

            Resource resource = gson.fromJson(response.toString(), resourceType);

            int[] colorRGB = ImageHelper.getHexColor(resource.getLogo());

            EmbedBuilder builder = new EmbedBuilder();
            Color color = new Color(45, 45, 45);
            if (colorRGB.length == 3) color = new Color(colorRGB[0], colorRGB[1], colorRGB[2]);

            setEmbedFooter(guild, builder, color);

            builder.setThumbnail(resource.getLogo());
            builder.setTitle(resource.getName(), resource.getResourceUrl());
            builder.setDescription(resource.getTag());

            var message = resource.getPrice() == 0 ? "Download Here" : "Buy here";
            Button button = new ButtonImpl("btn:link:resource" + resource.getId(), message, ButtonStyle.LINK, resource.getResourceUrl(), false, plugin.getEmote(guild));
            if (channel != null) {
                channel.sendMessageEmbeds(builder.build()).setActionRow(button).queue();
            }

        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}
