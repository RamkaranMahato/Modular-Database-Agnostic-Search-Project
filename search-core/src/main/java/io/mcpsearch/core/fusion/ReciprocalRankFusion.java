package io.mcpsearch.core.fusion;

import io.mcpsearch.core.model.FusionConfig;
import io.mcpsearch.core.model.FusionMode;
import io.mcpsearch.core.model.SearchResult;
import io.mcpsearch.core.spi.FusionStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Classic Reciprocal Rank Fusion: {@code score(doc) = sum over lists containing doc of 1 / (k + rank)}.
 * Uses only rank position, never the raw score value, so it needs no score
 * normalization and is insensitive to the two channels being on completely
 * different scales (BM25 vs. cosine similarity). This is the recommended
 * default fusion mode.
 */
public final class ReciprocalRankFusion implements FusionStrategy {

    @Override
    public List<SearchResult> fuse(List<SearchResult> textResults, List<SearchResult> vectorResults, FusionConfig config) {
        Objects.requireNonNull(textResults, "textResults must not be null");
        Objects.requireNonNull(vectorResults, "vectorResults must not be null");
        Objects.requireNonNull(config, "config must not be null");

        int k = config.rrfK();
        Map<String, Double> fusedScoreById = new LinkedHashMap<>();
        Map<String, Map<String, Object>> sourceById = new HashMap<>();
        Map<String, Double> textScoreById = new HashMap<>();
        Map<String, Double> vectorScoreById = new HashMap<>();

        accumulateRank(textResults, k, fusedScoreById, sourceById);
        for (SearchResult r : textResults) {
            textScoreById.put(r.id(), r.score());
        }

        accumulateRank(vectorResults, k, fusedScoreById, sourceById);
        for (SearchResult r : vectorResults) {
            vectorScoreById.put(r.id(), r.score());
        }

        List<Map.Entry<String, Double>> ranked = new ArrayList<>(fusedScoreById.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int limit = Math.min(config.topK(), ranked.size());
        List<SearchResult> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> entry = ranked.get(i);
            String id = entry.getKey();
            out.add(new SearchResult(
                    id,
                    entry.getValue(),
                    textScoreById.get(id),
                    vectorScoreById.get(id),
                    i + 1,
                    sourceById.getOrDefault(id, Map.of())
            ));
        }
        return out;
    }

    private void accumulateRank(
            List<SearchResult> results,
            int k,
            Map<String, Double> fusedScoreById,
            Map<String, Map<String, Object>> sourceById
    ) {
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            int rank = i + 1;
            double contribution = 1.0 / (k + rank);
            fusedScoreById.merge(r.id(), contribution, Double::sum);
            sourceById.putIfAbsent(r.id(), r.source());
        }
    }

    @Override
    public FusionMode mode() {
        return FusionMode.RRF;
    }
}
