package fr.maxlego08.zsupport.ai;

import fr.maxlego08.zsupport.lang.LangType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads and stores documentation chunks per plugin from a Docusaurus documentation site.
 * Supports English (plugins/{name}/docs/) and French (i18n/fr/docusaurus-plugin-content-docs-{name}/current/).
 * <p>
 * Uses Voyage AI embeddings for semantic search with a local file cache.
 * Falls back to keyword search if the Voyage API key is not configured.
 */
public class DocumentationStore {

    private static final int MAX_CHUNK_SIZE = 2000;

    // key: "pluginName:lang" -> list of chunks
    private final Map<String, List<DocumentationChunk>> store = new HashMap<>();

    // Parallel array: same key -> embeddings for each chunk (same order as store list)
    private final Map<String, List<float[]>> embeddings = new HashMap<>();

    private final EmbeddingService embeddingService = new EmbeddingService();
    private final EmbeddingCache embeddingCache = new EmbeddingCache();
    private boolean embeddingsAvailable = false;

    // Maps doc folder names to bot plugin names when they differ
    private static final Map<String, String> FOLDER_ALIASES = Map.of(
            "zitems", "zitem"
    );

    /**
     * Loads documentation from a Docusaurus project root and computes embeddings.
     */
    public void loadFromDocusaurus(Path docusaurusRoot) {
        store.clear();
        embeddings.clear();

        if (!Files.isDirectory(docusaurusRoot)) {
            System.out.println("[AI] Docusaurus root not found: " + docusaurusRoot);
            return;
        }

        // Load English docs
        Path pluginsDir = docusaurusRoot.resolve("plugins");
        if (Files.isDirectory(pluginsDir)) {
            loadPluginDocs(pluginsDir, "en");
        }

        // Load French docs
        Path frenchDir = docusaurusRoot.resolve("i18n").resolve("fr");
        if (Files.isDirectory(frenchDir)) {
            loadFrenchDocs(frenchDir);
        }

        int totalChunks = store.values().stream().mapToInt(List::size).sum();
        System.out.println("[AI] Documentation loaded: " + totalChunks + " chunks across " + store.size() + " plugin/language combinations");

        // Compute embeddings
        computeEmbeddings();
    }

    /**
     * Computes embeddings for all chunks, using cache when available.
     */
    private void computeEmbeddings() {
        embeddingCache.load();

        int cached = 0;
        int toCompute = 0;

        // First pass: identify which chunks need embedding
        Map<String, List<Integer>> needsEmbedding = new HashMap<>(); // storeKey -> list of chunk indices

        for (Map.Entry<String, List<DocumentationChunk>> entry : store.entrySet()) {
            String key = entry.getKey();
            List<DocumentationChunk> chunks = entry.getValue();
            List<float[]> chunkEmbeddings = new ArrayList<>();
            List<Integer> missingIndices = new ArrayList<>();

            for (int i = 0; i < chunks.size(); i++) {
                DocumentationChunk chunk = chunks.get(i);
                String hash = EmbeddingCache.hashContent(chunk.pluginName(), chunk.lang(), chunk.content());
                float[] cachedEmbedding = embeddingCache.get(hash);

                if (cachedEmbedding != null) {
                    chunkEmbeddings.add(cachedEmbedding);
                    cached++;
                } else {
                    chunkEmbeddings.add(null); // placeholder
                    missingIndices.add(i);
                    toCompute++;
                }
            }

            embeddings.put(key, chunkEmbeddings);
            if (!missingIndices.isEmpty()) {
                needsEmbedding.put(key, missingIndices);
            }
        }

        System.out.println("[AI] Embeddings: " + cached + " cached, " + toCompute + " to compute");

        if (toCompute == 0) {
            embeddingsAvailable = cached > 0;
            return;
        }

        // Second pass: batch compute missing embeddings
        for (Map.Entry<String, List<Integer>> entry : needsEmbedding.entrySet()) {
            String key = entry.getKey();
            List<Integer> indices = entry.getValue();
            List<DocumentationChunk> chunks = store.get(key);
            List<float[]> chunkEmbeddings = embeddings.get(key);

            // Prepare texts for batch embedding
            List<String> texts = new ArrayList<>();
            for (int idx : indices) {
                DocumentationChunk chunk = chunks.get(idx);
                // Include title for better embedding quality
                texts.add(chunk.sectionTitle() + "\n" + chunk.content());
            }

            System.out.println("[AI] Computing " + texts.size() + " embeddings for " + key + "...");
            List<float[]> computed = embeddingService.embedBatch(texts);

            if (computed.size() == texts.size()) {
                for (int i = 0; i < indices.size(); i++) {
                    int chunkIndex = indices.get(i);
                    float[] embedding = computed.get(i);
                    chunkEmbeddings.set(chunkIndex, embedding);

                    // Cache it
                    DocumentationChunk chunk = chunks.get(chunkIndex);
                    String hash = EmbeddingCache.hashContent(chunk.pluginName(), chunk.lang(), chunk.content());
                    embeddingCache.put(hash, embedding);
                }
                embeddingsAvailable = true;
            } else {
                System.out.println("[AI] Failed to compute embeddings for " + key + ", will use keyword search");
            }
        }

        // Save updated cache
        embeddingCache.save();
    }

    /**
     * Retrieves the most relevant chunks for a given plugin, query, and language.
     * Uses semantic search (embeddings) if available, otherwise falls back to keyword search.
     */
    public List<DocumentationChunk> findRelevantChunks(String pluginName, String query, LangType langType, int maxResults) {
        String lang = langType == LangType.FR ? "fr" : "en";
        String key = storeKey(pluginName.toLowerCase(), lang);

        List<DocumentationChunk> pluginChunks = store.get(key);
        List<float[]> pluginEmbeddings = embeddings.get(key);

        // Fallback to English if no docs in user's language
        if ((pluginChunks == null || pluginChunks.isEmpty()) && lang.equals("fr")) {
            key = storeKey(pluginName.toLowerCase(), "en");
            pluginChunks = store.get(key);
            pluginEmbeddings = embeddings.get(key);
        }

        if (pluginChunks == null || pluginChunks.isEmpty()) return List.of();

        // Use embeddings if available
        if (embeddingsAvailable && pluginEmbeddings != null && !pluginEmbeddings.isEmpty()) {
            return semanticSearch(pluginChunks, pluginEmbeddings, query, maxResults);
        }

        // Fallback: keyword search
        return keywordSearch(pluginChunks, query, maxResults);
    }

    private List<DocumentationChunk> semanticSearch(List<DocumentationChunk> chunks, List<float[]> chunkEmbeddings, String query, int maxResults) {
        float[] queryEmbedding = embeddingService.embed(query);
        if (queryEmbedding == null) {
            return keywordSearch(chunks, query, maxResults);
        }

        record Scored(DocumentationChunk chunk, double score) {}

        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            float[] chunkEmb = chunkEmbeddings.get(i);
            if (chunkEmb != null) {
                double similarity = EmbeddingService.cosineSimilarity(queryEmbedding, chunkEmb);
                scored.add(new Scored(chunks.get(i), similarity));
            }
        }

        return scored.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(maxResults)
                .map(Scored::chunk)
                .toList();
    }

    private List<DocumentationChunk> keywordSearch(List<DocumentationChunk> pluginChunks, String query, int maxResults) {
        String[] keywords = query.toLowerCase().split("\\s+");

        record Scored(DocumentationChunk chunk, double score) {}

        return pluginChunks.stream()
                .map(chunk -> new Scored(chunk, computeKeywordScore(chunk, keywords)))
                .filter(scored -> scored.score > 0)
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .limit(maxResults)
                .map(Scored::chunk)
                .toList();
    }

    public boolean hasDocumentation(String pluginName) {
        String lower = pluginName.toLowerCase();
        return store.containsKey(storeKey(lower, "en")) || store.containsKey(storeKey(lower, "fr"));
    }

    // ---- Loading methods ----

    private void loadPluginDocs(Path pluginsDir, String lang) {
        try (Stream<Path> pluginDirs = Files.list(pluginsDir)) {
            pluginDirs.filter(Files::isDirectory).forEach(pluginDir -> {
                String folderName = pluginDir.getFileName().toString().toLowerCase();
                String pluginName = FOLDER_ALIASES.getOrDefault(folderName, folderName);
                Path docsPath = pluginDir.resolve("docs");
                if (Files.isDirectory(docsPath)) {
                    List<DocumentationChunk> chunks = loadMarkdownFiles(pluginName, docsPath, lang);
                    if (!chunks.isEmpty()) {
                        store.put(storeKey(pluginName, lang), chunks);
                        System.out.println("[AI] Loaded " + chunks.size() + " chunks for " + pluginName + " (" + lang + ")");
                    }
                }
            });
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private void loadFrenchDocs(Path frenchDir) {
        try (Stream<Path> dirs = Files.list(frenchDir)) {
            dirs.filter(Files::isDirectory)
                    .filter(dir -> dir.getFileName().toString().startsWith("docusaurus-plugin-content-docs-"))
                    .forEach(dir -> {
                        String dirName = dir.getFileName().toString();
                        String folderName = dirName.substring("docusaurus-plugin-content-docs-".length()).toLowerCase();
                        String pluginName = FOLDER_ALIASES.getOrDefault(folderName, folderName);

                        Path currentPath = dir.resolve("current");
                        if (Files.isDirectory(currentPath)) {
                            List<DocumentationChunk> chunks = loadMarkdownFiles(pluginName, currentPath, "fr");
                            if (!chunks.isEmpty()) {
                                store.put(storeKey(pluginName, "fr"), chunks);
                                System.out.println("[AI] Loaded " + chunks.size() + " chunks for " + pluginName + " (fr)");
                            }
                        }
                    });
        } catch (IOException exception) {
            exception.printStackTrace();
        }
    }

    private List<DocumentationChunk> loadMarkdownFiles(String pluginName, Path docsPath, String lang) {
        List<DocumentationChunk> chunks = new ArrayList<>();

        try (Stream<Path> files = Files.walk(docsPath)) {
            files.filter(f -> f.toString().endsWith(".md")).forEach(file -> {
                try {
                    String rawContent = Files.readString(file);
                    String relativePath = docsPath.relativize(file).toString().replace('\\', '/');

                    ParsedDocument parsed = parseFrontmatter(rawContent);
                    String title = parsed.title != null ? parsed.title : relativePath;

                    chunks.addAll(splitIntoChunks(pluginName, relativePath, title, parsed.content, lang));
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            });
        } catch (IOException exception) {
            exception.printStackTrace();
        }

        return chunks;
    }

    // ---- Chunking ----

    private List<DocumentationChunk> splitIntoChunks(String pluginName, String fileName, String pageTitle, String content, String lang) {
        List<DocumentationChunk> chunks = new ArrayList<>();
        String[] lines = content.split("\n");

        StringBuilder currentContent = new StringBuilder();
        String currentTitle = pageTitle;

        for (String line : lines) {
            if (line.startsWith("#")) {
                if (!currentContent.isEmpty()) {
                    addChunk(chunks, pluginName, fileName, currentTitle, currentContent.toString(), lang);
                    currentContent = new StringBuilder();
                }
                currentTitle = line.replaceFirst("^#+\\s*", "").trim();
            }
            currentContent.append(line).append("\n");

            if (currentContent.length() > MAX_CHUNK_SIZE) {
                addChunk(chunks, pluginName, fileName, currentTitle, currentContent.toString(), lang);
                currentContent = new StringBuilder();
            }
        }

        if (!currentContent.isEmpty()) {
            addChunk(chunks, pluginName, fileName, currentTitle, currentContent.toString(), lang);
        }

        return chunks;
    }

    private void addChunk(List<DocumentationChunk> chunks, String pluginName, String fileName, String title, String content, String lang) {
        String trimmed = content.trim();
        if (!trimmed.isEmpty()) {
            chunks.add(new DocumentationChunk(pluginName, fileName, title, trimmed, lang));
        }
    }

    // ---- Scoring ----

    private double computeKeywordScore(DocumentationChunk chunk, String[] keywords) {
        String lowerContent = chunk.content().toLowerCase();
        String lowerTitle = chunk.sectionTitle().toLowerCase();
        double score = 0;

        for (String keyword : keywords) {
            if (keyword.length() < 3) continue;
            if (lowerTitle.contains(keyword)) score += 3.0;
            int index = 0;
            while ((index = lowerContent.indexOf(keyword, index)) != -1) {
                score += 1.0;
                index += keyword.length();
            }
        }

        return score;
    }

    // ---- Frontmatter ----

    private ParsedDocument parseFrontmatter(String rawContent) {
        if (!rawContent.startsWith("---")) {
            return new ParsedDocument(null, rawContent);
        }

        int endIndex = rawContent.indexOf("---", 3);
        if (endIndex == -1) {
            return new ParsedDocument(null, rawContent);
        }

        String frontmatter = rawContent.substring(3, endIndex);
        String content = rawContent.substring(endIndex + 3).trim();

        String title = null;
        for (String line : frontmatter.split("\n")) {
            String trimmedLine = line.trim();
            if (trimmedLine.startsWith("title:")) {
                title = trimmedLine.substring("title:".length()).trim();
                if (title.startsWith("\"") && title.endsWith("\"")) {
                    title = title.substring(1, title.length() - 1);
                }
                if (title.startsWith("'") && title.endsWith("'")) {
                    title = title.substring(1, title.length() - 1);
                }
                break;
            }
        }

        return new ParsedDocument(title, content);
    }

    private String storeKey(String pluginName, String lang) {
        return pluginName + ":" + lang;
    }

    private record ParsedDocument(String title, String content) {
    }
}
