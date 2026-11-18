package io.mcpsearch.adapter.mysql.sql;

/**
 * Similarity between two vectors, computed in-app because MySQL Community
 * Edition has no {@code DISTANCE()} function.
 * <p>
 * All three configurable metrics are implemented here so
 * {@code MysqlAdapterConfig.distanceMetric()} actually means something. Before
 * this, the adapter always computed cosine regardless of configuration, so
 * setting {@code EUCLIDEAN} silently did nothing - config that lies is worse
 * than config that doesn't exist.
 * <p>
 * Every metric is returned in "higher is better" form so the ranking code can
 * sort descending without caring which was used.
 */
public final class VectorSimilarity {

    private VectorSimilarity() {
    }

    /**
     * @param metric one of {@code COSINE}, {@code DOT}, {@code EUCLIDEAN}
     * @return similarity, higher meaning more similar; 0 when the vectors are
     *         unusable (length mismatch or empty)
     */
    public static double between(float[] a, float[] b, String metric) {
        if (a.length != b.length || a.length == 0) {
            return 0.0;
        }
        return switch (metric) {
            case "DOT" -> dot(a, b);
            case "EUCLIDEAN" -> euclideanSimilarity(a, b);
            default -> cosine(a, b);
        };
    }

    /** Cosine similarity in [-1, 1]; magnitude-independent, the usual choice for text. */
    public static double cosine(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Raw inner product - unbounded, and sensitive to vector magnitude. */
    public static double dot(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }

    /**
     * Euclidean (L2) distance mapped to a similarity via {@code 1 / (1 + distance)},
     * so that identical vectors score 1.0 and similarity falls off as distance grows -
     * keeping the "higher is better" contract that raw L2 distance would invert.
     */
    public static double euclideanSimilarity(float[] a, float[] b) {
        double sumSquares = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = (double) a[i] - b[i];
            sumSquares += diff * diff;
        }
        return 1.0 / (1.0 + Math.sqrt(sumSquares));
    }
}
