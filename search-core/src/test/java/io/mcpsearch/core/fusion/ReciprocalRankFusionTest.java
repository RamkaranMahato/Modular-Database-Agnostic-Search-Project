package io.mcpsearch.core.fusion;

import io.mcpsearch.core.model.FusionConfig;
import io.mcpsearch.core.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReciprocalRankFusionTest {

    private final ReciprocalRankFusion rrf = new ReciprocalRankFusion();

    private static SearchResult result(String id, double score) {
        return SearchResult.of(id, score, 1, Map.of("name", id));
    }

    @Test
    void docPresentInBothChannelsOutranksSingleChannelDoc() {
        List<SearchResult> text = List.of(result("B", 9.0), result("A", 8.0), result("C", 1.0));
        List<SearchResult> vector = List.of(result("A", 0.95), result("D", 0.5));

        List<SearchResult> fused = rrf.fuse(text, vector, FusionConfig.defaults(10));

        assertEquals("A", fused.get(0).id());
        assertEquals(1, fused.get(0).rank());
        assertNotNull(fused.get(0).textScore());
        assertNotNull(fused.get(0).vectorScore());
    }

    @Test
    void emptyVectorChannelPreservesTextOrdering() {
        List<SearchResult> text = List.of(result("X", 5.0), result("Y", 3.0));

        List<SearchResult> fused = rrf.fuse(text, List.of(), FusionConfig.defaults(10));

        assertEquals(2, fused.size());
        assertEquals("X", fused.get(0).id());
    }

    @Test
    void topKLimitsResultCount() {
        List<SearchResult> text = List.of(result("A", 3.0), result("B", 2.0), result("C", 1.0));

        List<SearchResult> fused = rrf.fuse(text, List.of(), new FusionConfig(0.5, 60, 2));

        assertEquals(2, fused.size());
    }

    @Test
    void ranksAreRenumberedFromOne() {
        List<SearchResult> text = List.of(result("A", 3.0), result("B", 2.0));

        List<SearchResult> fused = rrf.fuse(text, List.of(), FusionConfig.defaults(10));

        assertTrue(fused.get(0).rank() == 1 && fused.get(1).rank() == 2);
    }
}
