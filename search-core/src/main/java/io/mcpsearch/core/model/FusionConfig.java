package io.mcpsearch.core.model;

/**
 * Parameters passed to a {@link io.mcpsearch.core.spi.FusionStrategy}.
 * Both tuning knobs have sane defaults ({@link #defaults(int)}) so callers
 * using {@link FusionMode#RRF} never need to think about {@code alpha}, and
 * callers using {@link FusionMode#WEIGHTED} never need to think about {@code rrfK}.
 *
 * @param alpha weight given to the text channel in {@link FusionMode#WEIGHTED}
 *              (0.0 = vector only, 1.0 = text only). Ignored by RRF.
 * @param rrfK  the "k" constant in the RRF formula {@code 1 / (k + rank)};
 *              60 is the value used in the original RRF paper and is a
 *              reasonable default across result-set sizes. Ignored by WEIGHTED.
 * @param topK  maximum number of fused results to return
 */
public record FusionConfig(double alpha, int rrfK, int topK) {

    private static final double DEFAULT_ALPHA = 0.5;
    private static final int DEFAULT_RRF_K = 60;

    public FusionConfig {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in [0.0, 1.0], was " + alpha);
        }
        if (rrfK < 1) {
            throw new IllegalArgumentException("rrfK must be >= 1, was " + rrfK);
        }
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be >= 1, was " + topK);
        }
    }

    public static FusionConfig defaults(int topK) {
        return new FusionConfig(DEFAULT_ALPHA, DEFAULT_RRF_K, topK);
    }
}
