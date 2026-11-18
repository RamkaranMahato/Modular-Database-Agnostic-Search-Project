package io.mcpsearch.adapter.mysql.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class VectorSimilarityTest {

    private static final float[] UNIT = {1f, 0f};
    private static final float[] SAME_DIRECTION_LONGER = {2f, 0f};
    private static final float[] ORTHOGONAL = {0f, 1f};

    @Test
    void cosineIgnoresMagnitude() {
        assertEquals(1.0, VectorSimilarity.between(UNIT, SAME_DIRECTION_LONGER, "COSINE"), 1e-6);
        assertEquals(0.0, VectorSimilarity.between(UNIT, ORTHOGONAL, "COSINE"), 1e-6);
    }

    @Test
    void dotReflectsMagnitude() {
        assertEquals(2.0, VectorSimilarity.between(UNIT, SAME_DIRECTION_LONGER, "DOT"), 1e-6);
    }

    @Test
    void euclideanIsHigherIsBetterAndPeaksAtIdentical() {
        assertEquals(1.0, VectorSimilarity.between(UNIT, UNIT, "EUCLIDEAN"), 1e-6);
        // distance 1 -> 1 / (1 + 1)
        assertEquals(0.5, VectorSimilarity.between(UNIT, SAME_DIRECTION_LONGER, "EUCLIDEAN"), 1e-6);
    }

    @Test
    void configuredMetricActuallyChangesTheResult() {
        // The bug this guards: the adapter previously computed cosine no matter what was configured.
        double cosine = VectorSimilarity.between(UNIT, SAME_DIRECTION_LONGER, "COSINE");
        double dot = VectorSimilarity.between(UNIT, SAME_DIRECTION_LONGER, "DOT");
        double euclidean = VectorSimilarity.between(UNIT, SAME_DIRECTION_LONGER, "EUCLIDEAN");

        assertNotEquals(cosine, dot);
        assertNotEquals(dot, euclidean);
    }

    @Test
    void mismatchedOrEmptyVectorsScoreZero() {
        assertEquals(0.0, VectorSimilarity.between(UNIT, new float[]{1f, 0f, 0f}, "COSINE"), 1e-6);
        assertEquals(0.0, VectorSimilarity.between(new float[0], new float[0], "COSINE"), 1e-6);
    }
}
