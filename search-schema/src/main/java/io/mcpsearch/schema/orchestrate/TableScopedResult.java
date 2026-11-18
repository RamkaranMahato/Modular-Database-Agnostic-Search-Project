package io.mcpsearch.schema.orchestrate;

import io.mcpsearch.core.model.SearchResult;

import java.util.Objects;

/**
 * A {@link SearchResult} tagged with the table it came from. Necessary
 * because whole-schema search returns a single ranked list drawn from
 * multiple tables whose rows have entirely different {@code source} shapes -
 * the consumer needs to know a given hit is a {@code products} row vs. a
 * {@code return_policies} row to make any sense of its fields.
 */
public record TableScopedResult(String table, SearchResult result) {

    public TableScopedResult {
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(result, "result must not be null");
    }
}
