package io.mcpsearch.schema.select;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CosineTest {

    @Test
    void identicalVectorsScoreOne() {
        assertEquals(1.0, Cosine.similarity(new float[]{1, 0}, new float[]{1, 0}), 1e-6);
    }

    @Test
    void orthogonalVectorsScoreZero() {
        assertEquals(0.0, Cosine.similarity(new float[]{1, 0}, new float[]{0, 1}), 1e-6);
    }

    @Test
    void zeroVectorScoresZero() {
        assertEquals(0.0, Cosine.similarity(new float[]{0, 0}, new float[]{1, 1}), 1e-6);
    }

    @Test
    void lengthMismatchThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> Cosine.similarity(new float[]{1, 0}, new float[]{1, 0, 0}));
    }
}
