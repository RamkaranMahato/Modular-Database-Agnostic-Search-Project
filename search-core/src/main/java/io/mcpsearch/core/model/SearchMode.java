package io.mcpsearch.core.model;

/**
 * Which retrieval channel(s) a {@link SearchQuery} should use.
 * <p>
 * Adapters are expected to support all three; {@code TEXT} and {@code VECTOR}
 * exist mainly for debugging, benchmarking one channel in isolation, or
 * degrading gracefully when an adapter can't do one side (e.g. a plain
 * relational adapter with no vector column configured).
 */
public enum SearchMode {
    TEXT,
    VECTOR,
    HYBRID
}
