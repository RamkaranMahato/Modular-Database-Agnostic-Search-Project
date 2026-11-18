package io.mcpsearch.adapter.mysql.sql;

import java.util.List;

/**
 * A parameterized SQL statement: text with {@code ?} placeholders plus the
 * values to bind, in placeholder order.
 */
public record PreparedSql(String sql, List<Object> params) {

    public PreparedSql {
        params = List.copyOf(params);
    }
}
