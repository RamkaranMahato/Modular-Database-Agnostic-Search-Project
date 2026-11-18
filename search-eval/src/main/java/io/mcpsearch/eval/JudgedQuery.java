package io.mcpsearch.eval;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One judged query in the golden set: the query text, and which document ids a
 * human considers relevant for it.
 * <p>
 * Building this by hand is the unglamorous part of search work and also the
 * part that makes every later decision measurable. Thirty judged queries is
 * enough to catch a regression; you do not need thousands to start.
 *
 * @param query    the search text to run
 * @param mode     which search mode to evaluate (TEXT / VECTOR / HYBRID), or null for the default
 * @param relevant ids judged relevant, optionally with a grade (higher = more relevant).
 *                 Use 1.0 throughout for simple binary judgements.
 */
public record JudgedQuery(String query, String mode, Map<String, Double> relevant) {

    public JudgedQuery {
        relevant = Map.copyOf(relevant);
    }

    /** Convenience for binary judgements: every listed id counts as fully relevant. */
    public static JudgedQuery of(String query, String mode, List<String> relevantIds) {
        return new JudgedQuery(query, mode,
                relevantIds.stream().collect(java.util.stream.Collectors.toMap(id -> id, id -> 1.0, (a, b) -> a)));
    }

    public Set<String> relevantIds() {
        return relevant.keySet();
    }
}
