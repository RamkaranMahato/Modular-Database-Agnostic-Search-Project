package io.mcpsearch.adapter.mysql.sql;

import io.mcpsearch.adapter.mysql.MysqlAdapterConfig;
import io.mcpsearch.core.model.Document;
import io.mcpsearch.core.model.Filter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure SQL construction for {@code MysqlSearchAdapter} - no JDBC, so it's
 * unit-testable without a database. The MySQL-specific shapes:
 * <ul>
 *   <li>text: {@code MATCH(cols) AGAINST(? IN NATURAL LANGUAGE MODE)} - the
 *       same expression appears in the SELECT list (as the relevance score)
 *       and the WHERE clause (as the filter);</li>
 *   <li>vector: {@code DISTANCE(col, STRING_TO_VECTOR(?), 'COSINE')}, turned
 *       into a similarity as {@code 1 - DISTANCE(...)} to match the
 *       "higher is better" convention the rest of the system uses;</li>
 *   <li>upsert: {@code INSERT ... AS new ON DUPLICATE KEY UPDATE col = new.col}
 *       (the 8.0.19+ row-alias form, avoiding the deprecated {@code VALUES()}).</li>
 * </ul>
 * Reserved result labels {@code __rank}/{@code __similarity} carry the score
 * and {@code __total} carries {@code COUNT(*) OVER()} back to the adapter.
 */
public final class MysqlQueryBuilder {

    private MysqlQueryBuilder() {
    }

    public static PreparedSql buildTextQuery(MysqlAdapterConfig config, String queryText, List<Filter> filters, int limit) {
        List<Object> params = new ArrayList<>();
        String select = buildSelectClause(config);
        String table = SqlIdentifiers.quote(config.tableName());
        String matchExpr = "MATCH(" + quotedTextColumns(config) + ") AGAINST(? IN NATURAL LANGUAGE MODE)";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(select)
                .append(", ").append(matchExpr).append(" AS __rank")
                .append(", COUNT(*) OVER() AS __total")
                .append(" FROM ").append(table)
                .append(" WHERE ").append(matchExpr);
        params.add(queryText); // for the SELECT-list MATCH
        params.add(queryText); // for the WHERE MATCH

        String filterFragment = FilterSqlRenderer.render(filters, params);
        if (!filterFragment.isEmpty()) {
            sql.append(" AND ").append(filterFragment);
        }
        sql.append(" ORDER BY __rank DESC LIMIT ?");
        params.add(limit);

        return new PreparedSql(sql.toString(), params);
    }

    public static PreparedSql buildVectorQuery(MysqlAdapterConfig config, float[] queryVector, List<Filter> filters, int limit) {
        // Deprecated: MySQL Community has no DISTANCE(). Kept only to avoid breaking the signature;
        // the adapter now uses buildVectorCandidateQuery + in-app cosine instead. Do not call.
        throw new UnsupportedOperationException(
                "MySQL Community Edition lacks DISTANCE(); use buildVectorCandidateQuery with in-app cosine ranking");
    }

    /**
     * Fetches candidate rows for the vector channel, returning each row's stored embedding as a
     * string (via {@code VECTOR_TO_STRING}, which IS available in MySQL Community, unlike
     * {@code DISTANCE()} which is HeatWave/MySQL AI only). The adapter then parses these vectors
     * and ranks by cosine similarity in Java.
     * <p>
     * Only rows whose vector column is non-null are returned - a NULL embedding can't be compared.
     * There's no {@code ORDER BY distance} here (the DB can't compute it); ranking happens in-app,
     * so {@code limit} bounds the candidate pool rather than the final ranked output.
     *
     * @return SQL whose result set includes all select columns plus a reserved
     *         {@code __vector} column holding the row's embedding as a bracketed string
     */
    public static PreparedSql buildVectorCandidateQuery(MysqlAdapterConfig config, List<Filter> filters, int limit) {
        if (config.vectorColumn() == null) {
            throw new IllegalStateException("vectorColumn is not configured for this adapter");
        }
        List<Object> params = new ArrayList<>();
        String select = buildSelectClause(config);
        String table = SqlIdentifiers.quote(config.tableName());
        String vectorColumn = SqlIdentifiers.quote(config.vectorColumn());

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(select)
                .append(", VECTOR_TO_STRING(").append(vectorColumn).append(") AS __vector")
                .append(", COUNT(*) OVER() AS __total")
                .append(" FROM ").append(table)
                .append(" WHERE ").append(vectorColumn).append(" IS NOT NULL");

        String filterFragment = FilterSqlRenderer.render(filters, params);
        if (!filterFragment.isEmpty()) {
            sql.append(" AND ").append(filterFragment);
        }

        sql.append(" LIMIT ?");
        params.add(limit);

        return new PreparedSql(sql.toString(), params);
    }

    public static PreparedSql buildUpsert(MysqlAdapterConfig config, Document document) {
        List<Object> params = new ArrayList<>();
        List<String> columns = new ArrayList<>();

        columns.add(config.idColumn());
        params.add(document.id());

        for (Map.Entry<String, Object> field : document.fields().entrySet()) {
            SqlIdentifiers.validate(field.getKey(), "document field");
            columns.add(field.getKey());
            params.add(field.getValue());
        }

        boolean hasVector = config.vectorColumn() != null && document.vector() != null;
        if (hasVector) {
            columns.add(config.vectorColumn());
        }

        StringBuilder sql = new StringBuilder("INSERT INTO ").append(SqlIdentifiers.quote(config.tableName())).append(" (");
        appendQuotedList(sql, columns);
        sql.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(columns.get(i).equals(config.vectorColumn()) ? "STRING_TO_VECTOR(?)" : "?");
        }
        // Row-alias form (MySQL 8.0.19+), avoids the deprecated VALUES() function.
        sql.append(") AS new ON DUPLICATE KEY UPDATE ");

        boolean first = true;
        for (String column : columns) {
            if (column.equals(config.idColumn())) {
                continue;
            }
            if (!first) {
                sql.append(", ");
            }
            first = false;
            String quoted = SqlIdentifiers.quote(column);
            sql.append(quoted).append(" = new.").append(quoted);
        }

        if (hasVector) {
            params.add(VectorLiteral.toJsonArray(document.vector()));
        }

        return new PreparedSql(sql.toString(), params);
    }

    public static PreparedSql buildDelete(MysqlAdapterConfig config, String id) {
        String sql = "DELETE FROM " + SqlIdentifiers.quote(config.tableName())
                + " WHERE " + SqlIdentifiers.quote(config.idColumn()) + " = ?";
        return new PreparedSql(sql, List.of(id));
    }

    private static String quotedTextColumns(MysqlAdapterConfig config) {
        StringBuilder sb = new StringBuilder();
        List<String> cols = config.textColumns();
        for (int i = 0; i < cols.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(SqlIdentifiers.quote(cols.get(i)));
        }
        return sb.toString();
    }

    private static String buildSelectClause(MysqlAdapterConfig config) {
        if (config.selectColumns().isEmpty()) {
            return "*";
        }
        Set<String> columns = new LinkedHashSet<>();
        columns.add(config.idColumn());
        columns.addAll(config.selectColumns());

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (String column : columns) {
            SqlIdentifiers.validate(column, "selectColumn");
            if (!first) {
                sb.append(", ");
            }
            first = false;
            sb.append(SqlIdentifiers.quote(column));
        }
        return sb.toString();
    }

    private static void appendQuotedList(StringBuilder sql, List<String> identifiers) {
        for (int i = 0; i < identifiers.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(SqlIdentifiers.quote(identifiers.get(i)));
        }
    }
}
