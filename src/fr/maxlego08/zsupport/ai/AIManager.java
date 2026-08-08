package fr.maxlego08.zsupport.ai;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;
import fr.maxlego08.zsupport.tickets.Ticket;
import fr.maxlego08.zsupport.tickets.storage.SqlManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AIManager {

    private final DocumentationStore documentationStore = new DocumentationStore();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = ZSupport.instance.getGson();

    private static final int MAX_CONTEXT_CHUNKS = 5;
    private static final int MAX_MESSAGE_LENGTH = 4000;

    public AIManager() {
        String docsPath = Config.documentationPath;
        if (docsPath != null && !docsPath.isBlank()) {
            this.documentationStore.loadFromDocusaurus(Path.of(docsPath));
        } else {
            System.out.println("[AI] documentationPath is not configured, AI responses disabled.");
        }
    }

    /**
     * Processes a user message in a ticket and generates an AI response if relevant documentation exists.
     */
    public void processMessage(Ticket ticket, String userMessage, TextChannel channel, Guild guild) {
        String pluginName = ticket.getPlugin().getName();

        if (!documentationStore.hasDocumentation(pluginName)) return;
        if (userMessage.length() < 15) return;

        SqlManager.service.execute(() -> {
            try {
                String response = generateResponse(pluginName, userMessage, ticket);
                if (response != null && !response.isBlank()) {
                    sendResponse(channel, guild, response, pluginName);
                }
            } catch (Exception exception) {
                System.out.println("[AI] Error generating response: " + exception.getMessage());
                exception.printStackTrace();
            }
        });
    }

    private String generateResponse(String pluginName, String userMessage, Ticket ticket) throws Exception {

        List<DocumentationChunk> relevantChunks = documentationStore.findRelevantChunks(
                pluginName, userMessage, ticket.getLangType(), MAX_CONTEXT_CHUNKS
        );
        if (relevantChunks.isEmpty()) return null;

        String documentationContext = relevantChunks.stream()
                .map(DocumentationChunk::toContextString)
                .collect(Collectors.joining("\n\n---\n\n"));

        String documentationUrl = Config.documentations.getOrDefault(pluginName, "https://docs.groupez.dev");

        String systemPrompt = buildSystemPrompt(pluginName, documentationContext, documentationUrl);

        String truncatedMessage = userMessage.length() > MAX_MESSAGE_LENGTH
                ? userMessage.substring(0, MAX_MESSAGE_LENGTH)
                : userMessage;

        return callClaudeAPI(systemPrompt, truncatedMessage);
    }

    private String buildSystemPrompt(String pluginName, String documentationContext, String documentationUrl) {
        return """
                You are a helpful support assistant for the Minecraft plugin "%s" on the GroupeZ Discord server.
                Your role is to help users solve their problems based ONLY on the official documentation provided below.

                Rules:
                - Only answer questions related to the plugin documentation below.
                - If the documentation does not contain the answer, say so clearly and suggest the user waits for a staff member.
                - Be concise, clear, and friendly.
                - Reply in the same language as the user's message (French or English).
                - Format your response for Discord (use markdown, code blocks with ```yaml or ```java when relevant).
                - Do not invent features or configurations that are not in the documentation.
                - When relevant, reference the documentation URL: %s
                - Maximum 1500 characters in your response.

                ## Documentation for %s:

                %s
                """.formatted(pluginName, documentationUrl, pluginName, documentationContext);
    }

    @SuppressWarnings("unchecked")
    private String callClaudeAPI(String systemPrompt, String userMessage) throws Exception {

        String apiKey = Config.claudeApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[AI] Claude API key is not configured.");
            return null;
        }

        Map<String, Object> requestBody = Map.of(
                "model", "claude-sonnet-4-20250514",
                "max_tokens", 1024,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of("role", "user", "content", userMessage)
                )
        );

        String jsonBody = gson.toJson(requestBody);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI("https://api.anthropic.com/v1/messages"))
                .header("Content-Type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("[AI] API error " + response.statusCode() + ": " + response.body());
            return null;
        }

        Map<String, Object> responseMap = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>() {}.getType());
        List<Map<String, Object>> content = (List<Map<String, Object>>) responseMap.get("content");

        if (content != null && !content.isEmpty()) {
            return (String) content.get(0).get("text");
        }

        return null;
    }

    private void sendResponse(TextChannel channel, Guild guild, String response, String pluginName) {
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("GroupeZ AI - " + pluginName);
        builder.setDescription(response);
        builder.setColor(new Color(88, 101, 242));
        builder.setTimestamp(OffsetDateTime.now());
        builder.setFooter(Calendar.getInstance().get(Calendar.YEAR) + " - " + guild.getName() + " | AI-generated, may contain errors", guild.getIconUrl());

        channel.sendMessageEmbeds(builder.build()).queue();
    }

    public DocumentationStore getDocumentationStore() {
        return documentationStore;
    }
}
