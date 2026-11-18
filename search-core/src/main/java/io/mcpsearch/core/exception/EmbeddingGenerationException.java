package io.mcpsearch.core.exception;

/**
 * Thrown by an {@link io.mcpsearch.core.spi.EmbeddingProvider} when it
 * cannot produce an embedding - upstream API failure, rate limit, malformed
 * input, etc.
 */
public class EmbeddingGenerationException extends SearchException {

    private static final long serialVersionUID = 1L;

    public EmbeddingGenerationException(String message) {
        super(message);
    }

    public EmbeddingGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
