package io.mcpsearch.core.model;

import java.util.Map;
import java.util.Objects;

/**
 * A record to be indexed via {@link io.mcpsearch.core.spi.SearchAdapter#index(Document)}.
 * <p>
 * {@code vector} is optional by design: callers that already have an
 * embedding (e.g. computed in bulk offline) can supply it directly, while
 * callers that don't can leave it {@code null} and let the adapter call an
 * {@link io.mcpsearch.core.spi.EmbeddingProvider} itself using {@code fields}.
 *
 * @param id     stable, caller-assigned identifier
 * @param fields arbitrary indexed/stored fields (also returned in {@link SearchResult#source()})
 * @param vector precomputed embedding, or {@code null} to let the adapter generate one
 */
public record Document(String id, Map<String, Object> fields, float[] vector) {

    public Document {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(fields, "fields must not be null");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        fields = Map.copyOf(fields);
        vector = vector == null ? null : vector.clone();
    }

    public Document(String id, Map<String, Object> fields) {
        this(id, fields, null);
    }

    /** Defensive copy - records don't clone array components automatically. */
    @Override
    public float[] vector() {
        return vector == null ? null : vector.clone();
    }
}
