package io.mcpsearch.core.spi;

import io.mcpsearch.core.model.FusionConfig;
import io.mcpsearch.core.model.FusionMode;
import io.mcpsearch.core.model.SearchResult;

import java.util.List;

/**
 * Combines a text-channel result list and a vector-channel result list into
 * one ranked list. Used by adapters whose underlying engine has no native
 * hybrid search support and therefore must run both channels separately.
 * <p>
 * Implementations: {@link io.mcpsearch.core.fusion.ReciprocalRankFusion},
 * {@link io.mcpsearch.core.fusion.WeightedSumFusion}. Obtain the right one
 * for a query via {@link io.mcpsearch.core.fusion.FusionStrategyFactory}.
 */
public interface FusionStrategy {

    /**
     * @param textResults   results from the text channel, best first (may be empty, not null)
     * @param vectorResults results from the vector channel, best first (may be empty, not null)
     * @param config        fusion parameters
     * @return fused results, best first, {@code rank} renumbered from 1, limited to {@code config.topK()}
     */
    List<SearchResult> fuse(List<SearchResult> textResults, List<SearchResult> vectorResults, FusionConfig config);

    /** Which {@link FusionMode} this strategy implements. */
    FusionMode mode();
}
