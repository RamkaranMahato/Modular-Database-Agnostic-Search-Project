package io.mcpsearch.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Runs a golden set of {@link JudgedQuery} against a search function and
 * reports per-query and averaged metrics.
 * <p>
 * The search function is injected as {@code query -> ranked ids}, so this
 * module depends on nothing - no adapter, no database, no Spring. Point it at a
 * real {@code SearchAdapter} in an integration test, or at a stub in a unit
 * test. That also makes A/B comparison trivial: run the same golden set through
 * two differently-configured search functions and diff the reports. This is how
 * you answer "is RRF actually better than weighted fusion for my data?" with a
 * number instead of an opinion.
 */
public final class EvaluationRunner {

    private final int k;

    public EvaluationRunner(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1");
        }
        this.k = k;
    }

    /**
     * @param goldenSet the judged queries
     * @param search    runs one query and returns result ids in rank order
     */
    public Report run(List<JudgedQuery> goldenSet, Function<JudgedQuery, List<String>> search) {
        List<QueryResult> perQuery = new ArrayList<>(goldenSet.size());
        for (JudgedQuery judged : goldenSet) {
            List<String> ranked = search.apply(judged);
            Set<String> relevant = judged.relevantIds();
            perQuery.add(new QueryResult(
                    judged.query(),
                    Metrics.precisionAtK(ranked, relevant, k),
                    Metrics.recallAtK(ranked, relevant, k),
                    Metrics.reciprocalRank(ranked, relevant),
                    Metrics.ndcgAtK(ranked, judged.relevant(), k),
                    Metrics.missed(ranked, relevant)));
        }
        return new Report(k, perQuery);
    }

    /** Metrics for a single query, plus the relevant ids it failed to return. */
    public record QueryResult(
            String query,
            double precision,
            double recall,
            double reciprocalRank,
            double ndcg,
            Set<String> missed
    ) {
    }

    /** Aggregate across the golden set. The averaged reciprocal rank is MRR. */
    public record Report(int k, List<QueryResult> perQuery) {

        public Report {
            perQuery = List.copyOf(perQuery);
        }

        public double meanPrecision() {
            return average(QueryResult::precision);
        }

        public double meanRecall() {
            return average(QueryResult::recall);
        }

        /** Mean reciprocal rank. */
        public double mrr() {
            return average(QueryResult::reciprocalRank);
        }

        public double meanNdcg() {
            return average(QueryResult::ndcg);
        }

        private double average(java.util.function.ToDoubleFunction<QueryResult> metric) {
            return perQuery.isEmpty() ? 0.0
                    : perQuery.stream().mapToDouble(metric).average().orElse(0.0);
        }

        /** Human-readable summary, including the worst queries - which is where to look first. */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Evaluated %d queries @k=%d%n", perQuery.size(), k));
            sb.append(String.format("  Precision@%d : %.3f%n", k, meanPrecision()));
            sb.append(String.format("  Recall@%d    : %.3f%n", k, meanRecall()));
            sb.append(String.format("  MRR          : %.3f%n", mrr()));
            sb.append(String.format("  NDCG@%d      : %.3f%n", k, meanNdcg()));

            List<QueryResult> worst = perQuery.stream()
                    .sorted((a, b) -> Double.compare(a.ndcg(), b.ndcg()))
                    .limit(3)
                    .toList();
            if (!worst.isEmpty()) {
                sb.append("  Worst queries (by NDCG):").append(System.lineSeparator());
                for (QueryResult qr : worst) {
                    sb.append(String.format("    %-40s ndcg=%.3f missed=%s%n",
                            qr.query(), qr.ndcg(), qr.missed()));
                }
            }
            return sb.toString();
        }
    }
}
