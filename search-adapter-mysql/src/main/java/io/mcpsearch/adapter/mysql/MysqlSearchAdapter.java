package io.mcpsearch.adapter.mysql;

import io.mcpsearch.adapter.mysql.sql.VectorSimilarity;
import io.mcpsearch.adapter.mysql.sql.MysqlQueryBuilder;
import io.mcpsearch.adapter.mysql.sql.PreparedSql;
import io.mcpsearch.adapter.mysql.sql.VectorStringParser;
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
 * {@link SearchAdapter} for MySQL 9.0+. Text channel via
 * {@code MATCH ... AGAINST} over a {@code FULLTEXT} index; vector channel via
 * the native {@code VECTOR} type and {@code DISTANCE()} function. Like the
 * Postgres adapter, MySQL has no single-query hybrid operator, so
 * {@link SearchMode#HYBRID} runs both channels separately and fuses them
 * in-app with a {@code search-core} {@link FusionStrategy}.
 * <p>
 * <strong>Requires MySQL 9.0+ for any vector/hybrid query</strong> - the
 * {@code VECTOR} type and {@code STRING_TO_VECTOR}/{@code DISTANCE} functions
 * don't exist before 9.0. Text-only queries work on 8.x too, but if you only
 * need text search there's no reason to carry the vector config.
 * <p>
 * Requires only {@code javax.sql.DataSource} at compile time; bring your own
 * pool and the MySQL JDBC driver at runtime.
 */
public final class MysqlSearchAdapter implements SearchAdapter {

    private static final int MIN_CANDIDATE_POOL = 50;
    private static final int CANDIDATE_POOL_MULTIPLIER = 5;

    private static final System.Logger LOGGER = System.getLogger(MysqlSearchAdapter.class.getName());

    private final DataSource dataSource;
    private final MysqlAdapterConfig config;
    private final EmbeddingProvider embeddingProvider;

    public MysqlSearchAdapter(DataSource dataSource, MysqlAdapterConfig config) {
        this(dataSource, config, null);
    }

    public MysqlSearchAdapter(DataSource dataSource, MysqlAdapterConfig config, EmbeddingProvider embeddingProvider) {
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
                    MysqlQueryBuilder.buildTextQuery(config, query.text(), query.filters(), candidatePoolSize),
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
                            "VECTOR mode requires a vector: none supplied on the query and no "
                                    + "EmbeddingProvider is configured on this adapter");
                }
            } else if (config.vectorColumn() == null) {
                if (query.mode() == SearchMode.VECTOR) {
                    throw new SearchException("VECTOR mode requires vectorColumn to be configured on this adapter");
                }
            } else {
                // Fetch WIDE (config.vectorCandidatePoolSize) because MySQL can't pre-sort by
                // similarity - we must see enough rows to rank honestly. But return only the top
                // `candidatePoolSize` after ranking, which is all fusion/paging needs.
                QueryOutcome outcome = executeVectorCandidates(
                        MysqlQueryBuilder.buildVectorCandidateQuery(
                                config, query.filters(), config.vectorCandidatePoolSize()),
                        queryVector,
                        candidatePoolSize);
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
     * In single-channel (TEXT / VECTOR) mode the adapter builds results with only the final
     * {@code score} set, leaving both breakdown fields null. For cleaner output, copy that score
     * into the matching breakdown field ({@code textScore} for TEXT, {@code vectorScore} for VECTOR)
     * so the caller sees which channel produced the result. The fusion path already fills both.
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
            // In HYBRID mode, a failed embedding (API down, unbilled key, rate limit) must not
            // sink the whole search - the text channel is still fully usable. Degrade to
            // text-only by returning null; the caller's "queryVector == null" branch handles it.
            // In VECTOR mode there's no text channel to fall back to, so the failure is fatal.
            if (query.mode() == SearchMode.VECTOR) {
                throw new SearchException("Failed to generate query embedding", e);
            }
            LOGGER.log(System.Logger.Level.WARNING,
                    "Query embedding failed; falling back to text-only results for this HYBRID query", e);
            return null;
        }
    }

    /**
     * Vector channel for MySQL Community: fetch candidate rows (each carrying its embedding as a
     * {@code __vector} string), parse each vector, compute cosine similarity against the query
     * vector in Java, then rank descending. This replaces the DB-side {@code DISTANCE()} that
     * Community Edition doesn't have.
     */
    private QueryOutcome executeVectorCandidates(PreparedSql prepared, float[] queryVector, int topN)
            throws SearchException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(prepared.sql())) {

            bindParams(ps, prepared.params());

            List<Scored> scored = new ArrayList<>();
            long total = 0;
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                int columnCount = meta.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> source = new LinkedHashMap<>();
                    float[] rowVector = null;
                    for (int i = 1; i <= columnCount; i++) {
                        String label = meta.getColumnLabel(i);
                        Object value = rs.getObject(i);
                        if (label.equals("__vector")) {
                            rowVector = value == null ? null : VectorStringParser.parse(value.toString());
                        } else if (label.equals("__total")) {
                            total = value == null ? 0 : ((Number) value).longValue();
                        } else {
                            source.put(label, value);
                        }
                    }
                    if (rowVector == null) {
                        continue; // no usable embedding
                    }
                    double similarity = VectorSimilarity.between(queryVector, rowVector, config.distanceMetric());
                    String id = String.valueOf(source.get(config.idColumn()));
                    scored.add(new Scored(id, similarity, source));
                }
            }

            scored.sort((a, b) -> Double.compare(b.score(), a.score()));

            // If we scanned exactly as many rows as the cap allows, there are probably more rows
            // we never looked at - and since MySQL can't pre-sort by similarity, the unscanned
            // ones could easily be better matches. Say so rather than silently returning
            // plausible-looking but incomplete rankings.
            if (scored.size() >= config.vectorCandidatePoolSize()) {
                LOGGER.log(System.Logger.Level.WARNING,
                        "Vector search scanned the full candidate cap ({0} rows); results may be incomplete "
                                + "because MySQL cannot sort by similarity. Raise vectorCandidatePoolSize, or move "
                                + "to a store with a native vector index (Postgres+pgvector / MySQL HeatWave).",
                        config.vectorCandidatePoolSize());
            }

            List<SearchResult> results = new ArrayList<>(Math.min(topN, scored.size()));
            int limit = Math.min(topN, scored.size());
            for (int i = 0; i < limit; i++) {
                Scored s = scored.get(i);
                results.add(SearchResult.of(s.id(), s.score(), i + 1, s.source()));
            }
            return new QueryOutcome(results, total);
        } catch (SQLException e) {
            throw new SearchException("MySQL vector candidate query failed", e);
        }
    }

    private record Scored(String id, double score, Map<String, Object> source) {
    }

    private QueryOutcome execute(PreparedSql prepared, String scoreColumnLabel) throws SearchException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(prepared.sql())) {

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
            throw new SearchException("MySQL search query failed", e);
        }
    }

    private void bindParams(PreparedStatement ps, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
    }

    @Override
    public void index(Document document) throws SearchException {
        PreparedSql prepared = MysqlQueryBuilder.buildUpsert(config, document);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(prepared.sql())) {
            bindParams(ps, prepared.params());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SearchException("MySQL index operation failed for id=" + document.id(), e);
        }
    }

    @Override
    public void delete(String id) throws SearchException {
        PreparedSql prepared = MysqlQueryBuilder.buildDelete(config, id);
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(prepared.sql())) {
            bindParams(ps, prepared.params());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new SearchException("MySQL delete operation failed for id=" + id, e);
        }
    }

    @Override
    public HealthStatus healthCheck() {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SELECT 1");
            if (config.vectorColumn() != null) {
                // Confirm this server actually has the 9.0+ vector functions before
                // claiming healthy - a text-config would pass SELECT 1 on 8.x and then
                // fail every hybrid query at runtime, which is worse than failing here.
                try {
                    st.executeQuery("SELECT STRING_TO_VECTOR('[1]')").close();
                } catch (SQLException e) {
                    return HealthStatus.down(
                            "Connected, but this MySQL server lacks VECTOR support (needs 9.0+) "
                                    + "even though vectorColumn is configured: " + e.getMessage());
                }
            }
            return HealthStatus.healthy();
        } catch (SQLException e) {
            return HealthStatus.down("MySQL connectivity check failed: " + e.getMessage());
        }
    }

    private record QueryOutcome(List<SearchResult> results, long total) {
    }
}
