package io.mcpsearch.schema.model;

import java.util.Objects;

/**
 * A foreign-key relationship between two tables, as read from
 * {@code information_schema}. Directional: {@code fromTable.fromColumn}
 * references {@code toTable.toColumn}.
 */
public record ForeignKey(String fromTable, String fromColumn, String toTable, String toColumn) {

    public ForeignKey {
        Objects.requireNonNull(fromTable, "fromTable must not be null");
        Objects.requireNonNull(fromColumn, "fromColumn must not be null");
        Objects.requireNonNull(toTable, "toTable must not be null");
        Objects.requireNonNull(toColumn, "toColumn must not be null");
    }
}
