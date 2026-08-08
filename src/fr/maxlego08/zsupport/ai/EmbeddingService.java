package fr.maxlego08.zsupport.ai;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.maxlego08.zsupport.Config;
import fr.maxlego08.zsupport.ZSupport;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the Voyage AI embedding API to convert text into numerical vectors.
 * Used for semantic search in documentation chunks.
 * <p>
 * Voyage AI is recommended by Anthropic for use with Claude.
 * Free tier: 200M tokens. Model: voyage-3-lite (fast, cheap, 512 dimensions).
 *
 * @see <a href="https://docs.voyageai.com/docs/embeddings">Voyage AI Docs</a>
 */
public class EmbeddingService {

    private static final String VOYAGE_API_URL = "https://api.voyageai.com/v1/embeddings";
    private static final String MODEL = "voyage-3-lite";
    private static final int MAX_BATCH_SIZE = 128; // Voyage AI max batch size

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = ZSupport.instance.getGson();

    /**
     * Computes embeddings for a single text string.
     */
    public float[] embed(String text) {
        List<float[]> results = embedBatch(List.of(text));
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Computes embeddings for a batch of texts in a single API call.
     * Automatically splits into sub-batches if the list exceeds the API limit.
     */
    public List<float[]> embedBatch(List<String> texts) {
        String apiKey = Config.voyageApiKey;
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("[AI] Voyage API key is not configured. Falling back to keyword search.");
            return List.of();
        }

        List<float[]> allEmbeddings = new ArrayList<>();

        // Process in batches of MAX_BATCH_SIZE
        for (int i = 0; i < texts.size(); i += MAX_BATCH_SIZE) {
            List<String> batch = texts.subList(i, Math.min(i + MAX_BATCH_SIZE, texts.size()));
            List<float[]> batchResult = callVoyageAPI(batch, apiKey);
            allEmbeddings.addAll(batchResult);

            if (batchResult.size() != batch.size()) {
                System.out.println("[AI] Warning: expected " + batch.size() + " embeddings but got " + batchResult.size());
                break;
            }
        }

        return allEmbeddings;
    }

    @SuppressWarnings("unchecked")
    private List<float[]> callVoyageAPI(List<String> texts, String apiKey) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "input", texts,
                    "model", MODEL
            );

            String jsonBody = gson.toJson(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(VOYAGE_API_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println("[AI] Voyage API error " + response.statusCode() + ": " + response.body());
                return List.of();
            }

            Map<String, Object> responseMap = gson.fromJson(response.body(), new TypeToken<Map<String, Object>>() {}.getType());
            List<Map<String, Object>> data = (List<Map<String, Object>>) responseMap.get("data");

            if (data == null) return List.of();

            List<float[]> embeddings = new ArrayList<>();
            for (Map<String, Object> item : data) {
                List<Double> embedding = (List<Double>) item.get("embedding");
                float[] vector = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    vector[i] = embedding.get(i).floatValue();
                }
                embeddings.add(vector);
            }

            return embeddings;
        } catch (Exception exception) {
            System.out.println("[AI] Voyage API call failed: " + exception.getMessage());
            exception.printStackTrace();
            return List.of();
        }
    }

    /**
     * Computes cosine similarity between two vectors.
     *
     * @return value between -1 and 1, where 1 means identical direction
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;

        double dotProduct = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        double denominator = Math.sqrt(normA) * Math.sqrt(normB);
        return denominator == 0 ? 0 : dotProduct / denominator;
    }
}
