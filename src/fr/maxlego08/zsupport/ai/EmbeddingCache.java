package fr.maxlego08.zsupport.ai;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import fr.maxlego08.zsupport.ZSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches embeddings to a JSON file so they don't need to be recomputed on every startup.
 * The cache key is a hash of the chunk content — if the documentation changes, the embedding is recomputed.
 */
public class EmbeddingCache {

    private static final Path CACHE_FILE = Path.of("embeddings-cache.json");

    private final Gson gson = ZSupport.instance.getGson();

    // contentHash -> embedding vector
    private Map<String, float[]> cache = new HashMap<>();

    public void load() {
        if (!Files.exists(CACHE_FILE)) return;

        try {
            String json = Files.readString(CACHE_FILE);
            Map<String, float[]> loaded = gson.fromJson(json, new TypeToken<Map<String, float[]>>() {}.getType());
            if (loaded != null) {
                this.cache = loaded;
                System.out.println("[AI] Loaded embedding cache: " + cache.size() + " entries");
            }
        } catch (IOException exception) {
            System.out.println("[AI] Failed to load embedding cache: " + exception.getMessage());
        }
    }

    public void save() {
        try {
            String json = gson.toJson(cache);
            Files.writeString(CACHE_FILE, json);
            System.out.println("[AI] Saved embedding cache: " + cache.size() + " entries");
        } catch (IOException exception) {
            System.out.println("[AI] Failed to save embedding cache: " + exception.getMessage());
        }
    }

    public float[] get(String contentHash) {
        return cache.get(contentHash);
    }

    public void put(String contentHash, float[] embedding) {
        cache.put(contentHash, embedding);
    }

    public boolean contains(String contentHash) {
        return cache.containsKey(contentHash);
    }

    /**
     * Creates a simple hash key from chunk content for cache lookup.
     */
    public static String hashContent(String pluginName, String lang, String content) {
        // Use a simple hash — content change = new hash = re-embed
        return pluginName + ":" + lang + ":" + content.hashCode();
    }
}
