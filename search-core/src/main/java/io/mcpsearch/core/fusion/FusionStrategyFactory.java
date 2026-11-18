package io.mcpsearch.core.fusion;

import io.mcpsearch.core.model.FusionMode;
import io.mcpsearch.core.spi.FusionStrategy;

/**
 * Resolves a {@link FusionMode} to its {@link FusionStrategy} implementation.
 * Stateless strategies are cheap; a new instance per call is intentional -
 * callers who want to avoid the allocation (e.g. the Spring starter, which
 * can just wire both as beans) are free to cache these themselves.
 */
public final class FusionStrategyFactory {

    private FusionStrategyFactory() {
    }

    public static FusionStrategy get(FusionMode mode) {
        return switch (mode) {
            case RRF -> new ReciprocalRankFusion();
            case WEIGHTED -> new WeightedSumFusion();
        };
    }
}
