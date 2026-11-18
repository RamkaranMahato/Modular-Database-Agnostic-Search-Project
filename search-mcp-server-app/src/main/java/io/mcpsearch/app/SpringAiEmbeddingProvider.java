package io.mcpsearch.app;

import io.mcpsearch.core.exception.EmbeddingGenerationException;
import io.mcpsearch.core.spi.EmbeddingProvider;

import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;

/**
 * Adapts Spring AI's {@link EmbeddingModel} to this project's
 * {@link EmbeddingProvider} SPI. This is the concrete bridge that makes the
 * "the database doesn't compute embeddings - a model does" story real: it's
 * the thing that actually calls the embedding model.
 * <p>
 * Kept as a tiny adapter class so the rest of the codebase depends only on
 * the {@code EmbeddingProvider} interface, not on Spring AI - swapping OpenAI
 * for Bedrock or a local model is a matter of which {@code EmbeddingModel}
 * bean Spring wires in, with no change here.
 */
public class SpringAiEmbeddingProvider implements EmbeddingProvider {

    private final EmbeddingModel embeddingModel;
    private final String modelName;
    private final int dimensions;

    public SpringAiEmbeddingProvider(EmbeddingModel embeddingModel, String modelName, int dimensions) {
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
        this.dimensions = dimensions;
    }

    @Override
    public float[] embed(String text) throws EmbeddingGenerationException {
        try {
            return embeddingModel.embed(text);
        } catch (RuntimeException e) {
            throw new EmbeddingGenerationException("Embedding call failed", e);
        }
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) throws EmbeddingGenerationException {
        try {
            // Spring AI's embed(List<String>) is a real batch call - one request, many vectors.
            return embeddingModel.embed(texts);
        } catch (RuntimeException e) {
            throw new EmbeddingGenerationException("Batch embedding call failed", e);
        }
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    @Override
    public String modelName() {
        return modelName;
    }
}
