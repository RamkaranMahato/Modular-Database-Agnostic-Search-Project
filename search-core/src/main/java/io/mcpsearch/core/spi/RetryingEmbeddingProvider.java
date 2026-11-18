package io.mcpsearch.core.spi;

import io.mcpsearch.core.exception.EmbeddingGenerationException;

import java.util.List;

/**
 * Retries embedding calls with exponential backoff. Wraps any
 * {@link EmbeddingProvider}.
 * <p>
 * Embedding providers are remote HTTP services, and remote services fail in two
 * very different ways. Transient failures - rate limits, timeouts, brief 5xx
 * blips - succeed on a second attempt moments later. Permanent failures - a bad
 * API key, an unknown model, no billing - will fail identically forever, and
 * retrying them just multiplies latency and cost before surfacing the same
 * error. This retries the first kind and gives up immediately on the second,
 * classified by {@link #isRetryable(Throwable)}.
 * <p>
 * Backoff doubles each attempt (default 200ms, 400ms, 800ms). Retrying a
 * rate-limited service immediately makes the overload worse; backing off gives
 * it room to recover.
 * <p>
 * Deliberately hand-rolled rather than pulling in Resilience4j, so
 * {@code search-core} stays dependency-free. An application already using
 * Resilience4j should prefer its richer policies (circuit breakers, bulkheads)
 * over this.
 */
public final class RetryingEmbeddingProvider implements EmbeddingProvider {

    private static final System.Logger LOG = System.getLogger(RetryingEmbeddingProvider.class.getName());

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final long DEFAULT_INITIAL_BACKOFF_MS = 200;

    private final EmbeddingProvider delegate;
    private final int maxAttempts;
    private final long initialBackoffMs;

    public RetryingEmbeddingProvider(EmbeddingProvider delegate) {
        this(delegate, DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF_MS);
    }

    public RetryingEmbeddingProvider(EmbeddingProvider delegate, int maxAttempts, long initialBackoffMs) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.delegate = delegate;
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
    }

    @Override
    public float[] embed(String text) throws EmbeddingGenerationException {
        return withRetry("embed", () -> delegate.embed(text));
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) throws EmbeddingGenerationException {
        return withRetry("embedBatch", () -> delegate.embedBatch(texts));
    }

    private <T> T withRetry(String operation, EmbeddingCall<T> call) throws EmbeddingGenerationException {
        EmbeddingGenerationException lastFailure = null;
        long backoff = initialBackoffMs;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return call.execute();
            } catch (EmbeddingGenerationException e) {
                lastFailure = e;
                if (!isRetryable(e) || attempt == maxAttempts) {
                    throw e;
                }
                LOG.log(System.Logger.Level.WARNING,
                        "{0} failed (attempt {1}/{2}), retrying in {3}ms: {4}",
                        operation, attempt, maxAttempts, backoff, e.getMessage());
                sleep(backoff);
                backoff *= 2;
            }
        }
        throw lastFailure;
    }

    private void sleep(long millis) throws EmbeddingGenerationException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            // Restore the flag - swallowing it would hide shutdown from the caller.
            Thread.currentThread().interrupt();
            throw new EmbeddingGenerationException("Interrupted while backing off before retry", ie);
        }
    }

    /**
     * Whether a failure is worth retrying. Inspects the message chain for the
     * signatures of transient trouble (rate limiting, timeouts, 5xx, connection
     * resets) and treats everything else - notably auth, quota and bad-request
     * errors - as permanent.
     * <p>
     * String matching is crude, but the alternative is coupling this module to a
     * specific HTTP client's exception types, which would defeat the point of the
     * SPI. Adapters that know their transport can subclass or replace this.
     */
    static boolean isRetryable(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase();
            boolean transientFailure = lower.contains("rate limit")
                    || lower.contains("429")
                    || lower.contains("timeout")
                    || lower.contains("timed out")
                    || lower.contains("temporarily")
                    || lower.contains("connection reset")
                    || lower.contains("503")
                    || lower.contains("502")
                    || lower.contains("500");
            boolean permanentFailure = lower.contains("invalid_api_key")
                    || lower.contains("unauthorized")
                    || lower.contains("401")
                    || lower.contains("insufficient_quota")
                    || lower.contains("403");
            if (permanentFailure) {
                return false;
            }
            if (transientFailure) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    @FunctionalInterface
    private interface EmbeddingCall<T> {
        T execute() throws EmbeddingGenerationException;
    }
}
