package io.mcpsearch.core.model;

/**
 * Comparison applied by a {@link Filter}. Every {@link io.mcpsearch.core.spi.SearchAdapter}
 * implementation is expected to translate all of these into its native query
 * language (SQL {@code WHERE}, Mongo query operators, Elasticsearch query DSL, etc.).
 */
public enum FilterOperator {
    EQ,
    NEQ,
    GT,
    GTE,
    LT,
    LTE,
    /** value must be a {@code List<?>} of exactly two elements: [from, to], inclusive. */
    RANGE,
    /** value must be a {@code List<?>}. */
    IN,
    /** value must be a {@code List<?>}. */
    NOT_IN,
    /** substring/contains match on a text field. */
    CONTAINS
}
