package io.mcpsearch.core.fusion;

import io.mcpsearch.core.model.FusionConfig;
import io.mcpsearch.core.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeightedSumFusionTest {

    private final WeightedSumFusion weighted = new WeightedSumFusion();

    private static SearchResult result(String id, double score) {
        return SearchResult.of(id, score, 1, Map.of("name", id));
    }

    private static final List<SearchResult> TEXT =
            List.of(result("A", 1.0), result("B", 5.0), result("C", 3.0));
    private static final List<SearchResult> VECTOR =
            List.of(result("A", 0.99), result("B", 0.1), result("C", 0.5));

    @Test
    void alphaOneMatchesTextOnlyRanking() {
        List<SearchResult> fused = weighted.fuse(TEXT, VECTOR, new FusionConfig(1.0, 60, 10));

        assertEquals(List.of("B", "C", "A"), fused.stream().map(SearchResult::id).toList());
    }

    @Test
    void alphaZeroMatchesVectorOnlyRanking() {
        List<SearchResult> fused = weighted.fuse(TEXT, VECTOR, new FusionConfig(0.0, 60, 10));

        assertEquals(List.of("A", "C", "B"), fused.stream().map(SearchResult::id).toList());
    }

    @Test
    void equalScoresWithinAChannelDoNotDivideByZero() {
        List<SearchResult> text = List.of(result("A", 4.0), result("B", 4.0));

        List<SearchResult> fused = weighted.fuse(text, List.of(), new FusionConfig(0.5, 60, 10));

        assertEquals(2, fused.size());
    }
}
