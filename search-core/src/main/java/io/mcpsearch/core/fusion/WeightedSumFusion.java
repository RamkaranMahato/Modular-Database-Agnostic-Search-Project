package io.mcpsearch.core.fusion;

import io.mcpsearch.core.model.FusionConfig;
import io.mcpsearch.core.model.FusionMode;
import io.mcpsearch.core.model.SearchResult;
import io.mcpsearch.core.spi.FusionStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code score(doc) = alpha * normText(doc) + (1 - alpha) * normVector(doc)}.
 * Each channel is independently min-max normalized to [0, 1] before
 * combining, since raw BM25 and cosine-similarity scores are not on
 * comparable scales. Gives direct control over the text/vector balance via
 * {@link FusionConfig#alpha()}, at the cost of needing that value tuned -
 * prefer {@link ReciprocalRankFusion} unless you specifically need this control.
 */
public final class WeightedSumFusion implements FusionStrategy {

    @Override
    public List<SearchResult> fuse(List<SearchResult> textResults, List<SearchResult> vectorResults, FusionConfig config) {
        Objects.requireNonNull(textResults, "textResults must not be null");
        Objects.requireNonNull(vectorResults, "vectorResults must not be null");
        Objects.requireNonNull(config, "config must not be null");

        double alpha = config.alpha();
        Map<String, Double> normText = minMaxNormalize(textResults);
        Map<String, Double> normVector = minMaxNormalize(vectorResults);
        Map<String, Double> rawTextScore = rawScoreById(textResults);
        Map<String, Double> rawVectorScore = rawScoreById(vectorResults);
        Map<String, Map<String, Object>> sourceById = new HashMap<>();
        textResults.forEach(r -> sourceById.putIfAbsent(r.id(), r.source()));
        vectorResults.forEach(r -> sourceById.putIfAbsent(r.id(), r.source()));

        Set<String> ids = new LinkedHashSet<>();
        ids.addAll(normText.keySet());
        ids.addAll(normVector.keySet());

        Map<String, Double> combinedById = new HashMap<>();
        for (String id : ids) {
            double t = normText.getOrDefault(id, 0.0);
            double v = normVector.getOrDefault(id, 0.0);
            combinedById.put(id, alpha * t + (1 - alpha) * v);
        }

        List<Map.Entry<String, Double>> ranked = new ArrayList<>(combinedById.entrySet());
        ranked.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int limit = Math.min(config.topK(), ranked.size());
        List<SearchResult> out = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Double> entry = ranked.get(i);
            String id = entry.getKey();
            out.add(new SearchResult(
                    id,
                    entry.getValue(),
                    rawTextScore.get(id),
                    rawVectorScore.get(id),
                    i + 1,
                    sourceById.getOrDefault(id, Map.of())
            ));
        }
        return out;
    }

    private Map<String, Double> minMaxNormalize(List<SearchResult> results) {
        if (results.isEmpty()) {
            return Map.of();
        }
        double min = results.stream().mapToDouble(SearchResult::score).min().orElseThrow();
        double max = results.stream().mapToDouble(SearchResult::score).max().orElseThrow();
        double range = max - min;
        Map<String, Double> normalized = new HashMap<>();
        for (SearchResult r : results) {
            // All-equal scores (including the single-result case) normalize to 1.0:
            // within this channel, every candidate is equally relevant.
            double n = range == 0.0 ? 1.0 : (r.score() - min) / range;
            normalized.put(r.id(), n);
        }
        return normalized;
    }

    private Map<String, Double> rawScoreById(List<SearchResult> results) {
        Map<String, Double> byId = new HashMap<>();
        for (SearchResult r : results) {
            byId.put(r.id(), r.score());
        }
        return byId;
    }

    @Override
    public FusionMode mode() {
        return FusionMode.WEIGHTED;
    }
}
