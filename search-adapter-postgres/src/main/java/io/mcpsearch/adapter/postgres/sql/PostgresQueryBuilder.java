package io.mcpsearch.adapter.postgres.sql;

import io.mcpsearch.adapter.postgres.PostgresAdapterConfig;
import io.mcpsearch.core.model.Document;
import io.mcpsearch.core.model.Filter;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure SQL construction for {@code PostgresSearchAdapter} - no JDBC here,
 * which is what makes this testable without a live database. Two reserved
 * result-column labels are used to hand scores back to the adapter without
 * a second round trip: {@code __rank} / {@code __similarity} for the
 * relevance score, and {@code __total} for the matching-row count via
 * {@code COUNT(*) OVER()}.
 */
public final class PostgresQueryBuilder {

    private PostgresQueryBuilder() {
    }

    public static PreparedSql buildTextQuery(PostgresAdapterConfig config, String queryText, List<Filter> filters, int limit) {
        List<Object> params = new ArrayList<>();
        String select = buildSelectClause(config);
        String table = SqlIdentifiers.quote(config.tableName());

        // The document side of the match. Two very different shapes:
        //
        //  * tsvectorColumn set  -> reference a stored tsvector column directly. A GIN index on
        //    that column is usable, so this is an index lookup regardless of table size. This is
        //    the only form that scales; on a large table it is the difference between a millisecond
        //    index probe and a sequential scan.
        //
        //  * otherwise -> compute to_tsvector() per row at query time. Fine for small tables and
        //    convenient (no schema change), but it reads and tokenises EVERY row.
        //
        // Note the language is inlined as a literal, not bound as a '?'. A parameterised regconfig
        // prevents the planner from matching an expression index built on to_tsvector('english', ...),
        // which would silently defeat indexing even when one exists. The value is validated as a
        // plain identifier by PostgresAdapterConfig, so inlining is safe.
        String lang = "'" + config.language() + "'";
        String tsVector = (config.tsvectorColumn() != null)
                ? SqlIdentifiers.quote(config.tsvectorColumn())
                : "to_tsvector(" + lang + ", " + config.textSearchExpression() + ")";
        String tsQuery = "plainto_tsquery(" + lang + ", ?)";

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(select)
                .append(", ts_rank(").append(tsVector).append(", ").append(tsQuery).append(") AS __rank");
        params.add(queryText);                       // ts_rank's query
        if (config.exactTotalHits()) {
            // COUNT(*) OVER() is an exact count of every matching row - and on a large table that
            // means materialising them all, which can cost more than the search itself.
            sql.append(", COUNT(*) OVER() AS __total");
        }
        sql.append(" FROM ").append(table)
                .append(" WHERE ").append(tsVector).append(" @@ ").append(tsQuery);
        params.add(queryText);                       // WHERE clause query

        String filterFragment = FilterSqlRenderer.render(filters, params);
        if (!filterFragment.isEmpty()) {
            sql.append(" AND ").append(filterFragment);
        }
        sql.append(" ORDER BY __rank DESC LIMIT ?");
        params.add(limit);

        return new PreparedSql(sql.toString(), params);
    }

    public static PreparedSql buildVectorQuery(PostgresAdapterConfig config, float[] queryVector, List<Filter> filters, int limit) {
        if (config.vectorColumn() == null) {
            throw new IllegalStateException("vectorColumn is not configured for this adapter");
        }
        List<Object> params = new ArrayList<>();
        String select = buildSelectClause(config);
        String table = SqlIdentifiers.quote(config.tableName());
        String vectorColumn = SqlIdentifiers.quote(config.vectorColumn());
        String vectorLiteral = PgVectorCodec.toLiteral(queryVector);

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(select)
                .append(", 1 - (").append(vectorColumn).append(" <=> ?::vector) AS __similarity");
        params.add(vectorLiteral);
        if (config.exactTotalHits()) {
            sql.append(", COUNT(*) OVER() AS __total");
        }
        sql.append(" FROM ").append(table);

        String filterFragment = FilterSqlRenderer.render(filters, params);
        if (!filterFragment.isEmpty()) {
            sql.append(" WHERE ").append(filterFragment);
        }

        sql.append(" ORDER BY ").append(vectorColumn).append(" <=> ?::vector ASC LIMIT ?");
        params.add(vectorLiteral);
        params.add(limit);

        return new PreparedSql(sql.toString(), params);
    }

    public static PreparedSql buildUpsert(PostgresAdapterConfig config, Document document) {
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
            sql.append(columns.get(i).equals(config.vectorColumn()) ? "?::vector" : "?");
        }
        sql.append(") ON CONFLICT (").append(SqlIdentifiers.quote(config.idColumn())).append(") DO UPDATE SET ");

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
            sql.append(quoted).append(" = EXCLUDED.").append(quoted);
        }

        if (hasVector) {
            params.add(PgVectorCodec.toLiteral(document.vector()));
        }

        return new PreparedSql(sql.toString(), params);
    }

    public static PreparedSql buildDelete(PostgresAdapterConfig config, String id) {
        String sql = "DELETE FROM " + SqlIdentifiers.quote(config.tableName())
                + " WHERE " + SqlIdentifiers.quote(config.idColumn()) + " = ?";
        return new PreparedSql(sql, List.of(id));
    }

    private static String buildSelectClause(PostgresAdapterConfig config) {
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
