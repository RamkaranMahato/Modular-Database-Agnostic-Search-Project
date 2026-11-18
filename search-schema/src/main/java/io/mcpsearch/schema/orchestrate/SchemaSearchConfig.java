package io.mcpsearch.schema.orchestrate;

import io.mcpsearch.schema.graph.Direction;

/**
 * Tuning for the whole-schema search pipeline. Defaults chosen for a large
 * (50+ table) schema where over-expansion is the main risk.
 *
 * @param shortlistSize   how many tables the embedding stage passes to refine
 * @param minSimilarity   cosine cutoff below which a table isn't even shortlisted
 * @param fkDirection     which way to walk foreign keys from matched tables
 * @param fkDepth         how many FK hops to expand (1 = direct neighbours only)
 * @param maxTables       hard cap on total tables searched per query (guards hub tables)
 * @param rrfK            RRF constant for cross-table fusion
 * @param topK            max results returned
 */
public record SchemaSearchConfig(
        int shortlistSize,
        double minSimilarity,
        Direction fkDirection,
        int fkDepth,
        int maxTables,
        int rrfK,
        int topK
) {

    public SchemaSearchConfig {
        if (shortlistSize < 1) throw new IllegalArgumentException("shortlistSize must be >= 1");
        if (fkDepth < 0) throw new IllegalArgumentException("fkDepth must be >= 0");
        if (maxTables < 1) throw new IllegalArgumentException("maxTables must be >= 1");
        if (rrfK < 1) throw new IllegalArgumentException("rrfK must be >= 1");
        if (topK < 1) throw new IllegalArgumentException("topK must be >= 1");
    }

    public static SchemaSearchConfig defaults() {
        return new SchemaSearchConfig(10, 0.25, Direction.OUTGOING, 1, 15, 60, 10);
    }
}
