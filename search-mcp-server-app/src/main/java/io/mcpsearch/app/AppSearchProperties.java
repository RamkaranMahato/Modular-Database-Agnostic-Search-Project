package io.mcpsearch.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * App-level knobs beyond what the starter's {@code search.*} covers.
 *
 * @param embeddingModelName label stored alongside vectors / used in logs
 * @param embeddingDimensions the model's output size; MUST match your VECTOR(n) column
 * @param schemaSearchEnabled whether to expose the optional whole-schema search tool
 * @param llmRefineEnabled    whether the whole-schema tool uses the LLM refine stage
 */
@ConfigurationProperties(prefix = "app.search")
public record AppSearchProperties(
        String embeddingModelName,
        Integer embeddingDimensions,
        Boolean schemaSearchEnabled,
        Boolean llmRefineEnabled,
        Double schemaMinSimilarity,
        Integer schemaShortlistSize,
        Integer schemaFkDepth,
        Integer schemaMaxTables,
        Integer schemaTopK,
        Boolean embeddingCacheEnabled,
        Integer embeddingCacheMaxEntries,
        Boolean embeddingRetryEnabled,
        Integer embeddingRetryMaxAttempts
) {

    public AppSearchProperties {
        embeddingModelName = embeddingModelName != null ? embeddingModelName : "text-embedding-3-small";
        embeddingDimensions = embeddingDimensions != null ? embeddingDimensions : 1536;
        schemaSearchEnabled = schemaSearchEnabled != null ? schemaSearchEnabled : false;
        llmRefineEnabled = llmRefineEnabled != null ? llmRefineEnabled : false;
        // Table selection compares the query against TABLE METADATA text ("Table: products.
        // Columns: id, title, body..."), not row content - so similarities are naturally low
        // (0.1-0.2 is common even for a genuinely relevant table). 0.25 turned out to reject
        // everything; 0.05 is permissive by design. Raise it only once tables have good
        // descriptions/comments to match against.
        schemaMinSimilarity = schemaMinSimilarity != null ? schemaMinSimilarity : 0.05;
        schemaShortlistSize = schemaShortlistSize != null ? schemaShortlistSize : 10;
        schemaFkDepth = schemaFkDepth != null ? schemaFkDepth : 1;
        schemaMaxTables = schemaMaxTables != null ? schemaMaxTables : 15;
        schemaTopK = schemaTopK != null ? schemaTopK : 10;
        // Caching query embeddings is nearly free and removes an API round-trip from every
        // repeated search, so it is on by default. Bounded to avoid unbounded growth.
        embeddingCacheEnabled = embeddingCacheEnabled != null ? embeddingCacheEnabled : true;
        embeddingCacheMaxEntries = embeddingCacheMaxEntries != null ? embeddingCacheMaxEntries : 10_000;
        // Retry only helps transient failures; permanent ones (bad key, no quota) fail fast.
        embeddingRetryEnabled = embeddingRetryEnabled != null ? embeddingRetryEnabled : true;
        embeddingRetryMaxAttempts = embeddingRetryMaxAttempts != null ? embeddingRetryMaxAttempts : 3;
    }
}
