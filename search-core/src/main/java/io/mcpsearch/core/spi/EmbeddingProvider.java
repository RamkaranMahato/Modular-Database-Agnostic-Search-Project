package io.mcpsearch.core.spi;

import io.mcpsearch.core.exception.EmbeddingGenerationException;

import java.util.List;

/**
 * Turns text into vectors. Deliberately separate from {@link SearchAdapter}:
 * which datastore you search and which model you embed with are independent
 * choices (e.g. Postgres + OpenAI, or Elasticsearch + a local model), and
 * conflating them would force every adapter implementation to also know
 * about every embedding backend.
 * <p>
 * Implementations: OpenAI, Amazon Bedrock, a locally-hosted model, etc.
 * Callers that already have precomputed vectors (see
 * {@link io.mcpsearch.core.model.Document#vector()} and
 * {@link io.mcpsearch.core.model.SearchQuery#vector()}) don't need this at all.
 */
public interface EmbeddingProvider {

    /**
     * @throws EmbeddingGenerationException if the embedding call fails
     */
    float[] embed(String text) throws EmbeddingGenerationException;

    /**
     * Batch form for indexing throughput. Default implementation just loops
     * {@link #embed(String)} - override when the backend has a real batch API.
     *
     * @throws EmbeddingGenerationException if any embedding call fails
     */
    default List<float[]> embedBatch(List<String> texts) throws EmbeddingGenerationException {
        List<float[]> results = new java.util.ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(embed(text));
        }
        return results;
    }

    /** Output vector dimensionality - adapters need this to size/validate their vector index. */
    int dimensions();

    /** Model identifier, for logging and for storing alongside indexed vectors. */
    String modelName();
}
