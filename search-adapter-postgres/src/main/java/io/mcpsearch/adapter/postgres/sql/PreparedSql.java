package io.mcpsearch.adapter.postgres.sql;

import java.util.List;

/**
 * A parameterized SQL statement: the text with {@code ?} placeholders, and
 * the values to bind, in the exact order the placeholders appear.
 */
public record PreparedSql(String sql, List<Object> params) {

    public PreparedSql {
        params = List.copyOf(params);
    }
}
