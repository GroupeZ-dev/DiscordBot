package fr.maxlego08.zsupport.ai;

public record DocumentationChunk(String pluginName, String fileName, String sectionTitle, String content, String lang) {

    /**
     * Formats this chunk for inclusion in an AI prompt context.
     */
    public String toContextString() {
        return "[" + pluginName + " - " + sectionTitle + " (" + fileName + ")]\n" + content;
    }
}
