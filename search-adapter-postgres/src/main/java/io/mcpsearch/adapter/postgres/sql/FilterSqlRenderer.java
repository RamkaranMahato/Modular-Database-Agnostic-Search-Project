package io.mcpsearch.adapter.postgres.sql;

import io.mcpsearch.core.model.Filter;

import java.util.List;

/**
 * Renders a list of {@link Filter} clauses (ANDed together) into a SQL
 * fragment plus parameters, appended to the caller's running parameter
 * list so the returned fragment's placeholder order lines up correctly
 * when concatenated into a larger query.
 */
public final class FilterSqlRenderer {

    private FilterSqlRenderer() {
    }

    /**
     * @param filters   clauses to render; may be empty
     * @param paramsOut running parameter list to append this fragment's bind values to,
     *                  in the same order they appear in the returned SQL
     * @return the WHERE fragment (no leading {@code WHERE}/{@code AND}), or {@code ""} if {@code filters} is empty
     */
    public static String render(List<Filter> filters, List<Object> paramsOut) {
        if (filters.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        boolean first = true;
        for (Filter filter : filters) {
            SqlIdentifiers.validate(filter.field(), "filter.field");
            if (!first) {
                sql.append(" AND ");
            }
            first = false;
            String column = SqlIdentifiers.quote(filter.field());
            switch (filter.operator()) {
                case EQ -> appendBinary(sql, column, "=", filter.value(), paramsOut);
                case NEQ -> appendBinary(sql, column, "<>", filter.value(), paramsOut);
                case GT -> appendBinary(sql, column, ">", filter.value(), paramsOut);
                case GTE -> appendBinary(sql, column, ">=", filter.value(), paramsOut);
                case LT -> appendBinary(sql, column, "<", filter.value(), paramsOut);
                case LTE -> appendBinary(sql, column, "<=", filter.value(), paramsOut);
                case RANGE -> {
                    List<?> bounds = (List<?>) filter.value();
                    sql.append(column).append(" BETWEEN ? AND ?");
                    paramsOut.add(bounds.get(0));
                    paramsOut.add(bounds.get(1));
                }
                case IN -> appendInClause(sql, column, (List<?>) filter.value(), paramsOut, true);
                case NOT_IN -> appendInClause(sql, column, (List<?>) filter.value(), paramsOut, false);
                case CONTAINS -> {
                    sql.append(column).append(" ILIKE ?");
                    paramsOut.add("%" + filter.value() + "%");
                }
            }
        }
        return sql.toString();
    }

    private static void appendBinary(StringBuilder sql, String column, String op, Object value, List<Object> paramsOut) {
        sql.append(column).append(' ').append(op).append(" ?");
        paramsOut.add(value);
    }

    private static void appendInClause(StringBuilder sql, String column, List<?> values, List<Object> paramsOut, boolean isIn) {
        if (values.isEmpty()) {
            // IN () / NOT IN () are invalid SQL; the set-theoretically correct
            // substitutes are "matches nothing" / "matches everything".
            sql.append(isIn ? "1=0" : "1=1");
            return;
        }
        sql.append(column).append(isIn ? " IN (" : " NOT IN (");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
            paramsOut.add(values.get(i));
        }
        sql.append(')');
    }
}
