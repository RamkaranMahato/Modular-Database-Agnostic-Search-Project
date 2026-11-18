package io.mcpsearch.schema.impl;

import io.mcpsearch.schema.model.ColumnKind;

import java.util.Set;

/**
 * Classifies a database column into a {@link ColumnKind} from its
 * {@code information_schema} type name and key role. Pure and table-driven,
 * so the classification rules are unit-testable without a database even
 * though the code that <em>reads</em> the type names ({@link JdbcSchemaProvider})
 * isn't.
 * <p>
 * Deliberately covers both Postgres and MySQL type vocabularies, since the
 * schema module sits above whichever adapter is in use.
 */
public final class ColumnClassifier {

    // Text-searchable string types across Postgres and MySQL.
    private static final Set<String> TEXT_TYPES = Set.of(
            "text", "varchar", "character varying", "char", "character",
            "tinytext", "mediumtext", "longtext", "nvarchar", "nchar", "clob", "citext");

    // Native vector types: pgvector's "vector", MySQL 9.0+'s "vector".
    private static final Set<String> VECTOR_TYPES = Set.of("vector");

    private ColumnClassifier() {
    }

    /**
     * @param sqlTypeName the type as reported by {@code information_schema.columns.data_type}
     *                    (or {@code udt_name} for Postgres extension types like {@code vector})
     * @param isPrimaryKey whether this column is (part of) the primary key
     * @return the column's search-capability classification
     */
    public static ColumnKind classify(String sqlTypeName, boolean isPrimaryKey) {
        if (isPrimaryKey) {
            return ColumnKind.ID;
        }
        String normalized = sqlTypeName == null ? "" : sqlTypeName.trim().toLowerCase();
        // Strip any length/precision suffix, e.g. "varchar(255)" -> "varchar", "vector(1536)" -> "vector".
        int paren = normalized.indexOf('(');
        if (paren >= 0) {
            normalized = normalized.substring(0, paren).trim();
        }
        if (VECTOR_TYPES.contains(normalized)) {
            return ColumnKind.VECTOR;
        }
        if (TEXT_TYPES.contains(normalized)) {
            return ColumnKind.TEXT;
        }
        return ColumnKind.OTHER;
    }
}
