package io.mcpsearch.schema.orchestrate;

import io.mcpsearch.core.model.SearchResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fuses per-table result lists into one cross-table ranked list using
 * Reciprocal Rank Fusion. RRF is the right choice here for the same reason
 * it is within a single table's hybrid search, only more so: relevance
 * scores from different tables (a {@code ts_rank} over {@code products} vs.
 * over {@code articles}) are even less comparable than text-vs-vector on one
 * table, and RRF's rank-only formula sidesteps that entirely.
 * <p>
 * Pure and deterministic - the whole cross-table ranking behaviour is
 * testable without a database. Note this reimplements the RRF arithmetic
 * rather than reusing {@code search-core}'s {@code ReciprocalRankFusion},
 * because that operates on two lists of {@code SearchResult} whereas this
 * fuses N lists while preserving each hit's origin-table tag - a different
 * enough shape that sharing code would mean contorting one to fit the other.
 */
public final class CrossTableFusion {

    private final int rrfK;

    public CrossTableFusion(int rrfK) {
        if (rrfK < 1) {
            throw new IllegalArgumentException("rrfK must be >= 1, was " + rrfK);
        }
        this.rrfK = rrfK;
    }

    /**
     * @param perTableResults ordered map of table name -> that table's ranked results (best first)
     * @param topK            max results to return
     * @return one fused list, best first, capped at {@code topK}, each hit tagged with its table
     */
    public List<TableScopedResult> fuse(Map<String, List<SearchResult>> perTableResults, int topK) {
        Map<String, Double> fusedScore = new LinkedHashMap<>();
        Map<String, TableScopedResult> byKey = new LinkedHashMap<>();

        for (Map.Entry<String, List<SearchResult>> entry : perTableResults.entrySet()) {
            String table = entry.getKey();
            List<SearchResult> results = entry.getValue();
            for (int i = 0; i < results.size(); i++) {
                SearchResult r = results.get(i);
                int rank = i + 1;
                // Key by table+id: the same id in two different tables is two distinct hits.
                String key = table + "\u0000" + r.id();
                fusedScore.merge(key, 1.0 / (rrfK + rank), Double::sum);
                byKey.putIfAbsent(key, new TableScopedResult(table, r));
            }
        }

        List<Map.Entry<String, Double>> ranked = new ArrayList<>(fusedScore.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int limit = Math.min(topK, ranked.size());
        List<TableScopedResult> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            String key = ranked.get(i).getKey();
            double score = ranked.get(i).getValue();
            TableScopedResult original = byKey.get(key);
            // Renumber rank within the fused list and carry the fused score.
            SearchResult r = original.result();
            SearchResult reranked = new SearchResult(
                    r.id(), score, r.textScore(), r.vectorScore(), i + 1, r.source());
            out.add(new TableScopedResult(original.table(), reranked));
        }
        return out;
    }
}
