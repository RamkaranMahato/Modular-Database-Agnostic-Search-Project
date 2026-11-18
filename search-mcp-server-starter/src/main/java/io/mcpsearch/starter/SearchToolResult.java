package io.mcpsearch.starter;

import io.mcpsearch.core.model.SearchResponse;
import io.mcpsearch.core.model.SearchResult;

import java.util.List;
import java.util.Map;

/**
 * JSON-friendly shape of a search response, returned by the MCP {@code search}
 * tool. A plain record on purpose - {@code search-core}'s {@link SearchResponse}
 * carries adapter-facing pagination semantics the MCP client doesn't need.
 */
public record SearchToolResult(List<SearchHit> results, long totalHits, long tookMs) {

    public static SearchToolResult from(SearchResponse response) {
        List<SearchHit> hits = response.results().stream().map(SearchHit::from).toList();
        return new SearchToolResult(hits, response.totalHits(), response.tookMs());
    }

    public record SearchHit(String id, double score, Double textScore, Double vectorScore, Map<String, Object> source) {
        public static SearchHit from(SearchResult result) {
            return new SearchHit(result.id(), result.score(), result.textScore(), result.vectorScore(), result.source());
        }
    }
}
