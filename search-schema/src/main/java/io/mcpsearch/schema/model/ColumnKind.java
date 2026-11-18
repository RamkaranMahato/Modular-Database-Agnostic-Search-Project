package io.mcpsearch.schema.model;

/**
 * How a column can participate in search - the basis for auto-discovering
 * which tables are searchable at all. A table with no {@code TEXT} and no
 * {@code VECTOR} column isn't a search candidate, regardless of anything else.
 */
public enum ColumnKind {
    /** char/varchar/text and similar - eligible for the full-text channel. */
    TEXT,
    /** a pgvector {@code vector} column - eligible for the semantic channel. */
    VECTOR,
    /** primary key / identity column. */
    ID,
    /** anything else - filterable/returnable but not itself searched. */
    OTHER
}
