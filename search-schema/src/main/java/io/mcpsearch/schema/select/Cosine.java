package io.mcpsearch.schema.select;

/**
 * Cosine similarity between two vectors. Used to rank table-metadata
 * embeddings against the query embedding during the shortlist stage. Pure
 * math - no dependencies, fully testable.
 */
public final class Cosine {

    private Cosine() {
    }

    /**
     * @throws IllegalArgumentException if the vectors differ in length
     * @return cosine similarity in [-1, 1]; 0 if either vector is all-zero
     */
    public static double similarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "vector length mismatch: " + a.length + " vs " + b.length);
        }
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
}
