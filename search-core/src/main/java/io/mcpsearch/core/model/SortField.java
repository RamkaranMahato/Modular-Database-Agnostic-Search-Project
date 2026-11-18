package io.mcpsearch.core.model;

import java.util.Objects;

/**
 * A single sort clause. When a {@link SearchQuery} carries no sort fields
 * (the common case), adapters sort by relevance score descending - explicit
 * sort fields are for the "sort by price" style of query, which by nature
 * overrides relevance ranking.
 */
public record SortField(String field, SortDirection direction) {

    public SortField {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        if (field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
    }

    public static SortField asc(String field) {
        return new SortField(field, SortDirection.ASC);
    }

    public static SortField desc(String field) {
        return new SortField(field, SortDirection.DESC);
    }

    public enum SortDirection {
        ASC,
        DESC
    }
}
