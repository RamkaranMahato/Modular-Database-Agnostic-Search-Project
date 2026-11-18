package io.mcpsearch.core.model;

/**
 * How the text-search result list and the vector-search result list are
 * combined into one ranked list when {@link SearchMode#HYBRID} is used.
 */
public enum FusionMode {

    /**
     * Reciprocal Rank Fusion. Combines results using only each result's
     * <em>rank</em> in its source list, not its raw score - so it needs no
     * score normalization and no tuning. This is the recommended default:
     * it behaves sensibly even when the text engine's BM25 scale and the
     * vector engine's cosine-similarity scale have nothing in common.
     */
    RRF,

    /**
     * Weighted sum of min-max normalized scores:
     * {@code alpha * normText + (1 - alpha) * normVector}.
     * Gives direct, interpretable control over the text/vector balance via
     * {@link FusionConfig#alpha()}, at the cost of needing that value tuned
     * per dataset.
     */
    WEIGHTED
}
