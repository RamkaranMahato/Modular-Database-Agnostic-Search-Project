package io.mcpsearch.schema.model;

import java.util.Objects;

/**
 * One column's schema-level metadata, as read from {@code information_schema}
 * (or supplied via config override).
 *
 * @param name    column name
 * @param sqlType the SQL type name as reported by the database (e.g. "varchar", "vector")
 * @param kind    search-capability classification
 * @param comment column comment/description if present, else {@code null} - fed into the
 *                embedding text for table selection, so a good comment improves relevance
 */
public record ColumnMetadata(String name, String sqlType, ColumnKind kind, String comment) {

    public ColumnMetadata {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(sqlType, "sqlType must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
