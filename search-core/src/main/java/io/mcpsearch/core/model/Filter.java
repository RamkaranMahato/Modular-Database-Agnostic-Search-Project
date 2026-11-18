package io.mcpsearch.core.model;

import java.util.List;
import java.util.Objects;

/**
 * A single structured filter clause, e.g. {@code price >= 10} or
 * {@code category IN (books, electronics)}. A {@link SearchQuery} carries a
 * list of these, ANDed together by convention - adapters that support OR/NOT
 * groupings can extend this later without breaking existing callers.
 *
 * @param field    the field/column/property name in the backing datastore
 * @param operator the comparison to apply
 * @param value    the operand; shape depends on {@code operator} (see {@link FilterOperator})
 */
public record Filter(String field, FilterOperator operator, Object value) {

    public Filter {
        Objects.requireNonNull(field, "field must not be null");
        Objects.requireNonNull(operator, "operator must not be null");
        if (field.isBlank()) {
            throw new IllegalArgumentException("field must not be blank");
        }
        if ((operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) && !(value instanceof List<?>)) {
            throw new IllegalArgumentException(operator + " requires a List value");
        }
        if (operator == FilterOperator.RANGE && (!(value instanceof List<?> l) || l.size() != 2)) {
            throw new IllegalArgumentException("RANGE requires a List value of exactly [from, to]");
        }
    }

    public static Filter eq(String field, Object value) {
        return new Filter(field, FilterOperator.EQ, value);
    }

    public static Filter neq(String field, Object value) {
        return new Filter(field, FilterOperator.NEQ, value);
    }

    public static Filter gt(String field, Object value) {
        return new Filter(field, FilterOperator.GT, value);
    }

    public static Filter gte(String field, Object value) {
        return new Filter(field, FilterOperator.GTE, value);
    }

    public static Filter lt(String field, Object value) {
        return new Filter(field, FilterOperator.LT, value);
    }

    public static Filter lte(String field, Object value) {
        return new Filter(field, FilterOperator.LTE, value);
    }

    public static Filter range(String field, Object from, Object to) {
        return new Filter(field, FilterOperator.RANGE, List.of(from, to));
    }

    public static Filter in(String field, List<?> values) {
        return new Filter(field, FilterOperator.IN, values);
    }

    public static Filter notIn(String field, List<?> values) {
        return new Filter(field, FilterOperator.NOT_IN, values);
    }

    public static Filter contains(String field, String substring) {
        return new Filter(field, FilterOperator.CONTAINS, substring);
    }
}
