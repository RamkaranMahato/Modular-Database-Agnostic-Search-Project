package io.mcpsearch.schema.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One table's schema-level metadata. Carries enough to (a) decide whether
 * the table is a search candidate, (b) build the text used to embed it for
 * query-relevance selection, and (c) construct a
 * {@code PostgresAdapterConfig} for it when it's chosen.
 *
 * @param name    table name
 * @param columns its columns
 * @param comment table comment/description if present, else {@code null}
 */
public record TableMetadata(String name, List<ColumnMetadata> columns, String comment) {

    public TableMetadata {
        Objects.requireNonNull(name, "name must not be null");
        columns = List.copyOf(columns);
    }

    /** A table is searchable if it has at least one TEXT or VECTOR column. */
    public boolean isSearchable() {
        return columns.stream().anyMatch(c -> c.kind() == ColumnKind.TEXT || c.kind() == ColumnKind.VECTOR);
    }

    public List<ColumnMetadata> textColumns() {
        return columns.stream().filter(c -> c.kind() == ColumnKind.TEXT).toList();
    }

    public Optional<ColumnMetadata> vectorColumn() {
        return columns.stream().filter(c -> c.kind() == ColumnKind.VECTOR).findFirst();
    }

    public Optional<ColumnMetadata> idColumn() {
        return columns.stream().filter(c -> c.kind() == ColumnKind.ID).findFirst();
    }

    /**
     * The text embedded to represent this table during query-relevance selection.
     * Deliberately includes column names and comments, since on a well-named schema
     * that's exactly the signal that lets "wireless mouse" match a {@code products}
     * table - and on a poorly-named schema ({@code t_ord_hdr}), it's why the LLM
     * refine step exists as a backstop.
     */
    public String toEmbeddingText() {
        StringBuilder sb = new StringBuilder("Table: ").append(name);
        if (comment != null && !comment.isBlank()) {
            sb.append(". Description: ").append(comment);
        }
        sb.append(". Columns: ");
        sb.append(String.join(", ", columns.stream().map(ColumnMetadata::name).toList()));
        return sb.toString();
    }
}
