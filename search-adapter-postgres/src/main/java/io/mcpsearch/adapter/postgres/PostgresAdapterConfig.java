package io.mcpsearch.adapter.postgres;

import io.mcpsearch.adapter.postgres.sql.SqlIdentifiers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Configuration for {@link PostgresSearchAdapter}. This is the entire
 * "plug and play" surface for Postgres: point it at a table and name the
 * columns - nothing else to write.
 *
 * @param tableName            table to search/index
 * @param idColumn             primary key / unique id column
 * @param textSearchExpression SQL expression evaluated inside {@code to_tsvector(language, ...)} -
 *                             usually just a column name, but can be e.g. {@code "title || ' ' || body"}.
 *                             This is developer-supplied configuration, inserted into generated SQL
 *                             as-is - never build it from untrusted/user-supplied input.
 * @param vectorColumn         a {@code vector} column (pgvector extension), or {@code null} if this
 *                             table has no vector channel - {@code VECTOR} and {@code HYBRID} queries
 *                             then run text-only.
 * @param language             {@code regconfig} name for {@code to_tsvector}/{@code plainto_tsquery} (default "english")
 * @param selectColumns        columns to return as {@link io.mcpsearch.core.model.SearchResult#source()};
 *                             empty means {@code SELECT *}
 */
public record PostgresAdapterConfig(
        String tableName,
        String idColumn,
        String textSearchExpression,
        String tsvectorColumn,
        String vectorColumn,
        String language,
        List<String> selectColumns,
        boolean exactTotalHits,
        Integer hnswEfSearch,
        Boolean hnswIterativeScan
) {

    public PostgresAdapterConfig {
        SqlIdentifiers.validate(tableName, "tableName");
        SqlIdentifiers.validate(idColumn, "idColumn");
        Objects.requireNonNull(textSearchExpression, "textSearchExpression must not be null");
        if (textSearchExpression.isBlank()) {
            throw new IllegalArgumentException("textSearchExpression must not be blank");
        }
        if (tsvectorColumn != null) {
            SqlIdentifiers.validate(tsvectorColumn, "tsvectorColumn");
        }
        if (vectorColumn != null) {
            SqlIdentifiers.validate(vectorColumn, "vectorColumn");
        }
        language = (language == null || language.isBlank()) ? "english" : language;
        // The language is inlined into SQL as a literal rather than bound as a parameter, because
        // a parameterised regconfig stops the planner matching a text-search index. Validate it
        // as a plain identifier so inlining it cannot inject anything.
        SqlIdentifiers.validate(language, "language");
        selectColumns = selectColumns == null ? List.of() : List.copyOf(selectColumns);
    }

    public static Builder builder(String tableName, String idColumn, String textSearchExpression) {
        return new Builder(tableName, idColumn, textSearchExpression);
    }

    public static final class Builder {
        private final String tableName;
        private final String idColumn;
        private final String textSearchExpression;
        private String tsvectorColumn;
        private String vectorColumn;
        private String language = "english";
        private final List<String> selectColumns = new ArrayList<>();
        private boolean exactTotalHits = true;
        private Integer hnswEfSearch;
        private Boolean hnswIterativeScan;

        private Builder(String tableName, String idColumn, String textSearchExpression) {
            this.tableName = tableName;
            this.idColumn = idColumn;
            this.textSearchExpression = textSearchExpression;
        }

        /**
         * Name of a pre-computed {@code tsvector} column (ideally a STORED generated column with a
         * GIN index). Strongly recommended for large tables - see the class javadoc.
         */
        public Builder tsvectorColumn(String tsvectorColumn) {
            this.tsvectorColumn = tsvectorColumn;
            return this;
        }

        public Builder vectorColumn(String vectorColumn) {
            this.vectorColumn = vectorColumn;
            return this;
        }

        /** Set false on large tables to skip the COUNT(*) OVER() full scan. */
        public Builder exactTotalHits(boolean exactTotalHits) {
            this.exactTotalHits = exactTotalHits;
            return this;
        }

        /** pgvector {@code hnsw.ef_search}: higher = better recall, slower. Null leaves the server default. */
        public Builder hnswEfSearch(Integer hnswEfSearch) {
            this.hnswEfSearch = hnswEfSearch;
            return this;
        }

        /** pgvector {@code hnsw.iterative_scan}: keeps filtered vector searches from under-returning. */
        public Builder hnswIterativeScan(Boolean hnswIterativeScan) {
            this.hnswIterativeScan = hnswIterativeScan;
            return this;
        }

        public Builder language(String language) {
            this.language = language;
            return this;
        }

        public Builder selectColumn(String column) {
            this.selectColumns.add(column);
            return this;
        }

        public PostgresAdapterConfig build() {
            return new PostgresAdapterConfig(tableName, idColumn, textSearchExpression, tsvectorColumn,
                    vectorColumn, language, selectColumns, exactTotalHits, hnswEfSearch, hnswIterativeScan);
        }
    }
}
