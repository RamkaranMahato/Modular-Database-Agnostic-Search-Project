package io.mcpsearch.core.model;

import java.util.Map;
import java.util.Objects;

/**
 * A single ranked hit.
 *
 * @param id         the {@link Document#id()} that produced this hit
 * @param score      final score after fusion (or the single-channel score in TEXT/VECTOR mode)
 * @param textScore  the text-channel score, or {@code null} if this hit came only from the vector channel
 * @param vectorScore the vector-channel score, or {@code null} if this hit came only from the text channel
 * @param rank       1-based position in the returned list
 * @param source     the stored fields for this document, as given to {@link io.mcpsearch.core.spi.SearchAdapter#index}
 */
public record SearchResult(
        String id,
        double score,
        Double textScore,
        Double vectorScore,
        int rank,
        Map<String, Object> source
) {

    public SearchResult {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, was " + rank);
        }
        source = Map.copyOf(source);
    }

    /** Convenience constructor for a single-channel (non-hybrid) result. */
    public static SearchResult of(String id, double score, int rank, Map<String, Object> source) {
        return new SearchResult(id, score, null, null, rank, source);
    }
}
