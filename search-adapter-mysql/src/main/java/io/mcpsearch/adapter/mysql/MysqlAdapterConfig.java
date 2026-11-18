package io.mcpsearch.adapter.mysql;

import io.mcpsearch.adapter.mysql.sql.SqlIdentifiers;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for {@link MysqlSearchAdapter}.
 * <p>
 * The big difference from the Postgres config: MySQL full-text search can't
 * run over an arbitrary SQL expression the way Postgres's {@code to_tsvector}
 * can. {@code MATCH(...) AGAINST(...)} requires the matched columns to be
 * <em>exactly</em> covered by a declared {@code FULLTEXT} index. So this
 * config takes a list of {@code textColumns}, and those columns must have a
 * matching {@code FULLTEXT} index in your schema, e.g.:
 * <pre>
 *   ALTER TABLE products ADD FULLTEXT INDEX ft_products (title, body);
 * </pre>
 *
 * @param tableName      table to search/index
 * @param idColumn       primary key / unique id column (needs a unique constraint for upsert)
 * @param textColumns    columns to full-text search; must be covered by a FULLTEXT index
 * @param vectorColumn   a {@code VECTOR} column (MySQL 9.0+), or {@code null} for text-only
 * @param distanceMetric one of {@code COSINE}, {@code DOT}, {@code EUCLIDEAN} (default COSINE)
 * @param selectColumns  columns to return as {@code source}; empty means {@code SELECT *}
 * @param vectorCandidatePoolSize how many rows the vector channel pulls back to rank in-app.
 *        This matters a lot on MySQL: without a {@code DISTANCE()} function the database
 *        <em>cannot sort by similarity</em>, so this LIMIT takes an arbitrary slice of rows and
 *        only that slice gets cosine-ranked. If it's smaller than your table, genuinely-similar
 *        rows can be missed entirely. Default 10000 - large enough to cover a whole small/medium
 *        table (correct results), and the adapter warns when a query actually hits the cap.
 *        For tables beyond this, in-app ranking is the wrong tool: use Postgres+pgvector (which
 *        sorts by distance in the database) or MySQL HeatWave.
 */
public record MysqlAdapterConfig(
        String tableName,
        String idColumn,
        List<String> textColumns,
        String vectorColumn,
        String distanceMetric,
        List<String> selectColumns,
        int vectorCandidatePoolSize
) {

    private static final List<String> ALLOWED_METRICS = List.of("COSINE", "DOT", "EUCLIDEAN");
    private static final int DEFAULT_VECTOR_CANDIDATE_POOL = 10_000;

    public MysqlAdapterConfig {
        SqlIdentifiers.validate(tableName, "tableName");
        SqlIdentifiers.validate(idColumn, "idColumn");
        if (textColumns == null || textColumns.isEmpty()) {
            throw new IllegalArgumentException("at least one textColumn is required");
        }
        for (String column : textColumns) {
            SqlIdentifiers.validate(column, "textColumn");
        }
        textColumns = List.copyOf(textColumns);
        if (vectorColumn != null) {
            SqlIdentifiers.validate(vectorColumn, "vectorColumn");
        }
        distanceMetric = (distanceMetric == null || distanceMetric.isBlank()) ? "COSINE" : distanceMetric.toUpperCase();
        if (!ALLOWED_METRICS.contains(distanceMetric)) {
            throw new IllegalArgumentException("distanceMetric must be one of " + ALLOWED_METRICS + ", was " + distanceMetric);
        }
        selectColumns = selectColumns == null ? List.of() : List.copyOf(selectColumns);
        vectorCandidatePoolSize = vectorCandidatePoolSize > 0
                ? vectorCandidatePoolSize
                : DEFAULT_VECTOR_CANDIDATE_POOL;
    }

    public static Builder builder(String tableName, String idColumn, List<String> textColumns) {
        return new Builder(tableName, idColumn, textColumns);
    }

    public static final class Builder {
        private final String tableName;
        private final String idColumn;
        private final List<String> textColumns;
        private String vectorColumn;
        private String distanceMetric = "COSINE";
        private final List<String> selectColumns = new ArrayList<>();
        private int vectorCandidatePoolSize;

        private Builder(String tableName, String idColumn, List<String> textColumns) {
            this.tableName = tableName;
            this.idColumn = idColumn;
            this.textColumns = textColumns;
        }

        public Builder vectorColumn(String vectorColumn) {
            this.vectorColumn = vectorColumn;
            return this;
        }

        public Builder distanceMetric(String distanceMetric) {
            this.distanceMetric = distanceMetric;
            return this;
        }

        public Builder selectColumn(String column) {
            this.selectColumns.add(column);
            return this;
        }

        /** See {@link MysqlAdapterConfig#vectorCandidatePoolSize()}. Pass 0 to keep the default. */
        public Builder vectorCandidatePoolSize(int size) {
            this.vectorCandidatePoolSize = size;
            return this;
        }

        public MysqlAdapterConfig build() {
            return new MysqlAdapterConfig(tableName, idColumn, textColumns, vectorColumn, distanceMetric,
                    selectColumns, vectorCandidatePoolSize);
        }
    }
}
