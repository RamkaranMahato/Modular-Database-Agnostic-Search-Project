package io.mcpsearch.eval;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Standard information-retrieval metrics, computed over a ranked list of
 * result ids against a judged set of relevant ids.
 * <p>
 * Why these exist: without measurement, "is hybrid better than pure vector?"
 * or "did lowering the similarity threshold help?" can only be answered by
 * eyeballing a few queries, which is how search systems quietly get worse.
 * Each metric answers a different question, which is why you want several:
 * <ul>
 *   <li>{@link #precisionAtK} - of what I showed, how much was relevant?
 *       (punishes noise)</li>
 *   <li>{@link #recallAtK} - of what was relevant, how much did I find?
 *       (punishes misses)</li>
 *   <li>{@link #reciprocalRank} - how high was the first good result?
 *       (matters when users only look at the top)</li>
 *   <li>{@link #ndcgAtK} - the rank-weighted one: being right at position 1
 *       counts more than being right at position 10, and it supports graded
 *       relevance rather than just yes/no.</li>
 * </ul>
 * All methods are pure functions over ids, so they need no database and no
 * running server - the harness can be unit-tested like any other logic.
 */
public final class Metrics {

    private Metrics() {
    }

    /** Fraction of the top-k results that are relevant. */
    public static double precisionAtK(List<String> ranked, Set<String> relevant, int k) {
        if (k <= 0 || ranked.isEmpty()) {
            return 0.0;
        }
        int limit = Math.min(k, ranked.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (relevant.contains(ranked.get(i))) {
                hits++;
            }
        }
        return (double) hits / limit;
    }

    /** Fraction of all relevant documents that appear in the top k. */
    public static double recallAtK(List<String> ranked, Set<String> relevant, int k) {
        if (relevant.isEmpty()) {
            return 0.0;
        }
        int limit = Math.min(k, ranked.size());
        int hits = 0;
        for (int i = 0; i < limit; i++) {
            if (relevant.contains(ranked.get(i))) {
                hits++;
            }
        }
        return (double) hits / relevant.size();
    }

    /** 1/rank of the first relevant result, or 0 if none appear. Averaged over queries this is MRR. */
    public static double reciprocalRank(List<String> ranked, Set<String> relevant) {
        for (int i = 0; i < ranked.size(); i++) {
            if (relevant.contains(ranked.get(i))) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /**
     * Normalized discounted cumulative gain at k, with graded relevance.
     * <p>
     * DCG sums each result's gain discounted by {@code log2(rank + 1)}, so a hit
     * at position 1 contributes far more than the same hit at position 10. NDCG
     * divides that by the best achievable DCG (the ideal ranking), giving a 0-1
     * score comparable across queries with different numbers of relevant docs.
     *
     * @param gains relevance grade per id (absent id = 0). Binary judgements work
     *              fine: use 1.0 for relevant.
     */
    public static double ndcgAtK(List<String> ranked, Map<String, Double> gains, int k) {
        if (k <= 0 || ranked.isEmpty() || gains.isEmpty()) {
            return 0.0;
        }
        double dcg = 0.0;
        int limit = Math.min(k, ranked.size());
        for (int i = 0; i < limit; i++) {
            double gain = gains.getOrDefault(ranked.get(i), 0.0);
            dcg += gain / log2(i + 2.0);   // i is 0-based, so rank = i+1, discount = log2(rank+1)
        }

        // Ideal DCG: the same gains, best-ordered.
        List<Double> ideal = gains.values().stream()
                .sorted((x, y) -> Double.compare(y, x))
                .toList();
        double idcg = 0.0;
        int idealLimit = Math.min(k, ideal.size());
        for (int i = 0; i < idealLimit; i++) {
            idcg += ideal.get(i) / log2(i + 2.0);
        }
        return idcg == 0.0 ? 0.0 : dcg / idcg;
    }

    /** Ids that were judged relevant but never returned - the concrete misses to go look at. */
    public static Set<String> missed(List<String> ranked, Set<String> relevant) {
        Set<String> misses = new LinkedHashSet<>(relevant);
        misses.removeAll(ranked);
        return misses;
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2);
    }
}
