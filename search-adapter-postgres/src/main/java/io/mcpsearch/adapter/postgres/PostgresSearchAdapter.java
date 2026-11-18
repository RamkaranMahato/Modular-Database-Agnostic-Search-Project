package io.mcpsearch.adapter.postgres;

import io.mcpsearch.adapter.postgres.sql.PostgresQueryBuilder;
import io.mcpsearch.adapter.postgres.sql.PreparedSql;
import io.mcpsearch.core.exception.EmbeddingGenerationException;
import io.mcpsearch.core.exception.SearchException;
import io.mcpsearch.core.fusion.FusionStrategyFactory;
import io.mcpsearch.core.model.Document;
import io.mcpsearch.core.model.HealthStatus;
import io.mcpsearch.core.model.SearchMode;
import io.mcpsearch.core.model.SearchQuery;
import io.mcpsearch.core.model.SearchResponse;
import io.mcpsearch.core.model.FusionConfig;
import io.mcpsearch.core.model.SearchResult;
import io.mcpsearch.core.spi.EmbeddingProvider;
import io.mcpsearch.core.spi.FusionStrategy;
import io.mcpsearch.core.spi.SearchAdapter;
import io.mcpsearch.core.util.Results;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@link SearchAdapter} for PostgreSQL: {@code tsvector}/{@code ts_rank} for
 * the text channel, the pgvector extension for the vector channel. Postgres
 * has no single-query hybrid operator combining the two, so
 * {@link io.mcpsearch.core.model.SearchMode#HYBRID} runs both channels as
 * separate queries and fuses them in-app via a {@link FusionStrategy} from
 * {@code search-core}.
 * <p>
 * Requires only {@code javax.sql.DataSource} at compile time - bring your
 * own connection pool (HikariCP, Spring's DataSource, etc.) and your own
 * PostgreSQL JDBC driver on the runtime classpath.
 */
public final class PostgresSearchAdapter implements SearchAdapter {

    private static final int MIN_CANDIDATE_POOL = 50;
    private static final int CANDIDATE_POOL_MULTIPLIER = 5;

    private static final System.Logger LOGGER = System.getLogger(PostgresSearchAdapter.class.getName());

    private final DataSource dataSource;
    private final PostgresAdapterConfig config;
    private final EmbeddingProvider embeddingProvider;

    public PostgresSearchAdapter(DataSource dataSource, PostgresAdapterConfig config) {
        this(dataSource, config, null);
    }

    /**
     * @param embeddingProvider optional. If supplied, queries in VECTOR/HYBRID mode that
     *                          carry text but no precomputed vector will have one generated
     *                          automatically. If {@code null}, such queries must supply a
     *                          precomputed vector via {@link SearchQuery#vector()}.
     */
    public PostgresSearchAdapter(DataSource dataSource, PostgresAdapterConfig config, EmbeddingProvider embeddingProvider) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.embeddingProvider = embeddingProvider;
    }

    @Override
    public SearchResponse search(SearchQuery query) throws SearchException {
        long start = System.currentTimeMillis();
        // Deep pages need a deeper pool: to serve page N we must first rank (N+1)*size results.
        int pageOffset = query.page() * query.size();
        int needed = pageOffset + query.size();
        int candidatePoolSize = Math.max(MIN_CANDIDATE_POOL, needed * CANDIDATE_POOL_MULTIPLIER);

        List<SearchResult> textResults = List.of();
        long textTotal = 0;
        if (query.mode() == SearchMode.TEXT || query.mode() == SearchMode.HYBRID) {
            QueryOutcome outcome = execute(
                    PostgresQueryBuilder.buildTextQuery(config, query.text(), query.filters(), candidatePoolSize),
                    "__rank");
            textResults = outcome.results();
            textTotal = outcome.total();
        }

        List<SearchResult> vectorResults = List.of();
        long vectorTotal = 0;
        if (query.mode() == SearchMode.VECTOR || query.mode() == SearchMode.HYBRID) {
            float[] queryVector = resolveQueryVector(query);
            if (queryVector == null) {
                if (query.mode() == SearchMode.VECTOR) {
                    throw new SearchException(
                            "VECTOR mode requires a vector: none was supplied on the query and no "
                                    + "EmbeddingProvider is configured on this adapter");
                }
                // HYBRID with no vector available -> degrade to text-only.
            } else if (config.vectorColumn() == null) {
                if (query.mode() == SearchMode.VECTOR) {
                    throw new SearchException("VECTOR mode requires vectorColumn to be configured on this adapter");
                }
                // HYBRID with no vector column configured -> degrade to text-only.
            } else {
                QueryOutcome outcome = execute(
                        PostgresQueryBuilder.buildVectorQuery(config, queryVector, query.filters(), candidatePoolSize),
                        "__similarity",
                        true);
                vectorResults = outcome.results();
                vectorTotal = outcome.total();
            }
        }

        List<SearchResult> finalResults;
        long totalHits;
        switch (query.mode()) {
            case TEXT -> {
                finalResults = withChannelScore(textResults, ChannelScore.TEXT);
                totalHits = textTotal;
            }
            case VECTOR -> {
                finalResults = withChannelScore(vectorResults, ChannelScore.VECTOR);
                totalHits = vectorTotal;
            }
            default -> {
                FusionStrategy fusion = FusionStrategyFactory.get(query.fusionMode());
                // Fuse enough rows to reach the requested page - the query's own FusionConfig
                // caps at one page's worth, which would make page 2 permanently empty.
                FusionConfig paged = new FusionConfig(
                        query.fusionConfig().alpha(), query.fusionConfig().rrfK(), needed);
                finalResults = fusion.fuse(textResults, vectorResults, paged);
                // Each channel's own COUNT(*) OVER() is exact for that channel, but the two
                // queries only fetch a bounded candidate pool each, so the true post-fusion
                // union size isn't knowable without scanning both fully. max() is a defensible
                // lower-bound approximation.
                totalHits = Math.max(textTotal, vectorTotal);
            }
        }

        // Explicit sort (if any) applies to the ranked candidates, then we slice the page.
        // Order matters: sorting after pagination would only sort within one page.
        finalResults = Results.applySort(finalResults, query.sort());
        finalResults = Results.paginate(finalResults, query.page(), query.size());

        long tookMs = System.currentTimeMillis() - start;
        return new SearchResponse(finalResults, totalHits, tookMs, query.page(), query.size());
    }

    private enum ChannelScore { TEXT, VECTOR }

    /**
     * In single-channel (TEXT / VECTOR) mode, copy the final score into the matching breakdown
     * field so the caller sees which channel produced each result. The fusion path already sets
     * both fields; this only affects single-channel modes. Kept identical to the MySQL adapter.
     */
    private List<SearchResult> withChannelScore(List<SearchResult> results, ChannelScore channel) {
        List<SearchResult> out = new ArrayList<>(results.size());
        for (SearchResult r : results) {
            Double textScore = channel == ChannelScore.TEXT ? r.score() : null;
            Double vectorScore = channel == ChannelScore.VECTOR ? r.score() : null;
            out.add(new SearchResult(r.id(), r.score(), textScore, vectorScore, r.rank(), r.source()));
        }
        return out;
    }


    private float[] resolveQueryVector(SearchQuery query) throws SearchException {
        if (query.vector() != null) {
            return query.vector();
        }
        if (embeddingProvider == null || query.text() == null || query.text().isBlank()) {
            return null;
        }
        try {
            return embeddingProvider.embed(query.text());
        } catch (EmbeddingGenerationException e) {
            // HYBRID degrades to text-only on embedding failure; VECTOR has no fallback. See the
            // MySQL adapter for the fuller rationale - kept identical for consistent behaviour.
            if (query.mode() == SearchMode.VECTOR) {
                throw new SearchException("Failed to generate query embedding", e);
            }
            LOGGER.log(System.Logger.Level.WARNING,
                    "Query embedding failed; falling back to text-only results for this HYBRID query", e);
            return null;
        }
    }

    private QueryOutcome execute(PreparedSql prepared, String scoreColumnLabel) throws SearchException {
        return execute(prepared, scoreColumnLabel, false);
    }

    private QueryOutcome execute(PreparedSql prepared, String scoreColumnLabel, boolean vectorQuery)
            throws SearchException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(prepared.sql())) {

            if (vectorQuery) {
                applyVectorSessionSettings(conn);
            }
            bindParams(ps, prepared.params());

            List<SearchResult> results = new ArrayList<>();
            long total = 0;
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                int rank = 1;
                while (rs.next()) {
                    Map<String, Object> source = new LinkedHashMap<>();
                    double score = 0;
                    for (int i = 1; i <= columnCount; i++) {
                        String label = meta.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        if (label.equals(scoreColumnLabel)) {
                            score = value == null ? 0 : ((Number) value).doubleValue();
                        } else if (label.equals("__total")) {
                            total = value == null ? 0 : ((Number) value).longValue();
                        } else {
                            source.put(label, value);
                        }
                    }
                    String id = String.valueOf(source.get(config.idColumn()));
                    results.add(SearchResult.of(id, score, rank++, source));
                }
            }
            return new QueryOutcome(results, total);
        } catch (SQLException e) {
            throw new SearchException("Postgres search query failed", e);
        }
    }

    /**
     * Applies pgvector's per-session tuning knobs before a vector query.
     * <p>
     * {@code hnsw.ef_search} controls how much of the HNSW graph is explored: higher means better
     * recall and slower queries. The default is deliberately low, so a system that cares about
     * recall usually has to raise it.
     * <p>
     * {@code hnsw.iterative_scan} addresses the classic filtered-vector-search trap. An HNSW scan
     * walks nearest-first and the filter is applied afterwards, so a selective WHERE clause can
     * leave you with far fewer than LIMIT rows - silently returning three results when ten exist.
     * Iterative scan keeps pulling from the index until the limit is satisfied.
     * <p>
     * These are plain SET statements on a pooled connection, so they persist for that connection
     * rather than just this statement. That is harmless here because every vector query wants the
     * same values, but it is why the values are validated integers/booleans from config and never
     * caller input.
     */
    private void applyVectorSessionSettings(Connection conn) {
        if (config.hnswEfSearch() == null && config.hnswIterativeScan() == null) {
            return;
        }
        try (Statement st = conn.createStatement()) {
            if (config.hnswEfSearch() != null) {
                st.execute("SET hnsw.ef_search = " + config.hnswEfSearch());
            }
            if (Boolean.TRUE.equals(config.hnswIterativeScan())) {
                st.execute("SET hnsw.iterative_scan = relaxed_order");
            }
        } catch (SQLException e) {
            // Older pgvector (or none) won't know these GUCs. Tuning is an optimisation, not a
            // requirement - the query still returns correct results without it.
            LOGGER.log(System.Logger.Level.WARNING,
                    "Could not apply pgvector session settings (older pgvector?): " + e.getMessage());
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    @Override
    public void index(Document document) throws SearchException {
        PreparedSql prepared = PostgresQueryBuilder.buildUpsert(config, document);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(prepared.sql())) {
            bindParams(ps, prepared.params());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SearchException("Postgres index operation failed for id=" + document.id(), e);
        }
    }

    @Override
    public void delete(String id) throws SearchException {
        PreparedSql prepared = PostgresQueryBuilder.buildDelete(config, id);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(prepared.sql())) {
            bindParams(ps, prepared.params());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SearchException("Postgres delete operation failed for id=" + id, e);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
            if (config.vectorColumn() != null) {
                try (ResultSet rs = st.executeQuery("SELECT 1 FROM pg_extension WHERE extname = 'vector'")) {
                    if (!rs.next()) {
                        return HealthStatus.down(
                                "Connected, but the 'vector' extension is not installed even though "
                                        + "vectorColumn is configured");
                    }
                }
            }
            return HealthStatus.healthy();
        } catch (SQLException e) {
            return HealthStatus.down("Postgres connectivity check failed: " + e.getMessage());
        }
    }

    private record QueryOutcome(List<SearchResult> results, long total) {
    }
}
