package io.mcpsearch.core.model;

import java.util.List;
import java.util.Objects;

/**
 * The full result of a {@link SearchQuery}.
 *
 * @param results   ranked hits for the requested page, best first
 * @param totalHits total matches across all pages (adapter's best count/estimate)
 * @param tookMs    wall-clock time the adapter spent executing the query, in milliseconds
 * @param page      the page number that was returned (0-based, echoes the request)
 * @param size      the page size that was requested
 */
public record SearchResponse(
        List<SearchResult> results,
        long totalHits,
        long tookMs,
        int page,
        int size
) {

    public SearchResponse {
        Objects.requireNonNull(results, "results must not be null");
        results = List.copyOf(results);
        if (totalHits < 0) {
            throw new IllegalArgumentException("totalHits must be >= 0");
        }
    }

    public static SearchResponse empty(int page, int size, long tookMs) {
        return new SearchResponse(List.of(), 0, tookMs, page, size);
    }
}
