package io.mcpsearch.core.spi;

import io.mcpsearch.core.exception.EmbeddingGenerationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caches embeddings by text, so repeating a query doesn't pay for the same
 * model call twice. Wraps any {@link EmbeddingProvider}.
 * <p>
 * Query embedding is the obvious win: every search embeds its query text, and
 * real traffic repeats queries constantly. An API round-trip (tens to hundreds
 * of milliseconds, plus cost) collapses to a map lookup.
 * <p>
 * Uses a bounded LRU rather than an unbounded map - an unbounded cache keyed by
 * arbitrary user input is a memory leak waiting for a traffic spike. Eviction is
 * least-recently-used, which matches the access pattern (popular queries stay
 * hot). No TTL: an embedding for a given text and model never goes stale, so
 * entries only need evicting for space, not freshness.
 * <p>
 * Thread-safe via a synchronized wrapper. That's a coarse lock, but a cache hit
 * is a fast in-memory operation, so contention is far cheaper than the API call
 * it avoids. Deliberately kept dependency-free (no Caffeine) so {@code search-core}
 * stays JDK-only.
 */
public final class CachingEmbeddingProvider implements EmbeddingProvider {

    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final EmbeddingProvider delegate;
    private final Map<String, float[]> cache;

    public CachingEmbeddingProvider(EmbeddingProvider delegate) {
        this(delegate, DEFAULT_MAX_ENTRIES);
    }

    public CachingEmbeddingProvider(EmbeddingProvider delegate, int maxEntries) {
        this.delegate = delegate;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @java.io.Serial
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > maxEntries;
            }
        });
    }

    @Override
    public float[] embed(String text) throws EmbeddingGenerationException {
        float[] cached = cache.get(text);
        if (cached != null) {
            return cached.clone();
        }
        float[] embedding = delegate.embed(text);
        cache.put(text, embedding.clone());
        return embedding;
    }

    /**
     * Batch form that only sends the cache misses downstream, then stitches the
     * results back into the caller's original order. A batch where half the texts
     * are already known costs half as much.
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) throws EmbeddingGenerationException {
        List<float[]> results = new ArrayList<>(Collections.nCopies(texts.size(), null));
        List<String> misses = new ArrayList<>();
        List<Integer> missIndexes = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            float[] cached = cache.get(texts.get(i));
            if (cached != null) {
                results.set(i, cached.clone());
            } else {
                misses.add(texts.get(i));
                missIndexes.add(i);
            }
        }

        if (!misses.isEmpty()) {
            List<float[]> fetched = delegate.embedBatch(misses);
            for (int i = 0; i < missIndexes.size() && i < fetched.size(); i++) {
                float[] embedding = fetched.get(i);
                cache.put(misses.get(i), embedding.clone());
                results.set(missIndexes.get(i), embedding);
            }
        }
        return results;
    }

    @Override
    public int dimensions() {
        return delegate.dimensions();
    }

    @Override
    public String modelName() {
        return delegate.modelName();
    }

    /** Current number of cached entries - useful for metrics and tests. */
    public int size() {
        return cache.size();
    }
}
