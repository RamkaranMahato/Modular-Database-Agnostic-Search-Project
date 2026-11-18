package io.mcpsearch.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsTest {

    @Test
    void precisionAndRecallCountHitsCorrectly() {
        List<String> ranked = List.of("a", "x", "b", "y", "z");
        Set<String> relevant = Set.of("a", "b", "c");

        assertEquals(0.4, Metrics.precisionAtK(ranked, relevant, 5), 1e-6);
        assertEquals(2.0 / 3, Metrics.recallAtK(ranked, relevant, 5), 1e-6);
        assertEquals(1.0, Metrics.precisionAtK(ranked, relevant, 1), 1e-6);
    }

    @Test
    void missedReportsRelevantIdsNeverReturned() {
        assertEquals(Set.of("c"), Metrics.missed(List.of("a", "b"), Set.of("a", "b", "c")));
    }

    @Test
    void reciprocalRankUsesFirstHitPosition() {
        assertEquals(1.0, Metrics.reciprocalRank(List.of("a", "b"), Set.of("a")), 1e-6);
        assertEquals(0.5, Metrics.reciprocalRank(List.of("x", "a"), Set.of("a")), 1e-6);
        assertEquals(0.0, Metrics.reciprocalRank(List.of("x", "y"), Set.of("a")), 1e-6);
    }

    @Test
    void ndcgIsOneForPerfectRankingAndZeroForNoHits() {
        Map<String, Double> gains = Map.of("a", 1.0, "b", 1.0);

        assertEquals(1.0, Metrics.ndcgAtK(List.of("a", "b", "z"), gains, 3), 1e-6);
        assertEquals(0.0, Metrics.ndcgAtK(List.of("x", "y", "z"), gains, 3), 1e-6);
    }

    @Test
    void ndcgRewardsPlacingRelevantResultsEarlier() {
        Map<String, Double> gains = Map.of("a", 1.0);

        double atRank1 = Metrics.ndcgAtK(List.of("a", "x", "y"), gains, 3);
        double atRank2 = Metrics.ndcgAtK(List.of("x", "a", "y"), gains, 3);
        double atRank3 = Metrics.ndcgAtK(List.of("x", "y", "a"), gains, 3);

        assertTrue(atRank1 > atRank2 && atRank2 > atRank3);
        assertEquals(1.0, atRank1, 1e-6);
        assertEquals(0.63093, atRank2, 1e-4);   // 1/log2(3) normalised by ideal 1/log2(2)
    }
}
