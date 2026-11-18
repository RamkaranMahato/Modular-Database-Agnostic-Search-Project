package io.mcpsearch.schema.orchestrate;

import io.mcpsearch.core.model.SearchResult;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrossTableFusionTest {

    private static SearchResult res(String id, int rank) {
        return new SearchResult(id, 1.0, null, null, rank, Map.of("k", id));
    }

    @Test
    void ranksAreRenumberedAndHitsTaggedWithTable() {
        Map<String, List<SearchResult>> perTable = new LinkedHashMap<>();
        perTable.put("products", List.of(res("p1", 1), res("p2", 2)));
        perTable.put("articles", List.of(res("a1", 1)));

        List<TableScopedResult> fused = new CrossTableFusion(60).fuse(perTable, 10);

        assertEquals(3, fused.size());
        assertEquals(1, fused.get(0).result().rank());
        assertEquals("p2", fused.get(2).result().id()); // rank-2 hit sorts last
        assertEquals("products", fused.get(2).table());
    }

    @Test
    void sameIdInDifferentTablesAreDistinctHits() {
        Map<String, List<SearchResult>> perTable = new LinkedHashMap<>();
        perTable.put("products", List.of(res("42", 1)));
        perTable.put("orders", List.of(res("42", 1)));

        List<TableScopedResult> fused = new CrossTableFusion(60).fuse(perTable, 10);

        assertEquals(2, fused.size());
        Set<String> tables = new HashSet<>();
        fused.forEach(f -> tables.add(f.table()));
        assertEquals(2, tables.size());
    }

    @Test
    void topKLimitsResults() {
        Map<String, List<SearchResult>> perTable = new LinkedHashMap<>();
        perTable.put("t", List.of(res("a", 1), res("b", 2), res("c", 3)));

        List<TableScopedResult> fused = new CrossTableFusion(60).fuse(perTable, 2);

        assertEquals(2, fused.size());
    }
}
