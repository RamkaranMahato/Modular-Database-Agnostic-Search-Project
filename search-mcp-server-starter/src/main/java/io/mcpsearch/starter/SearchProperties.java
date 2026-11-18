package io.mcpsearch.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the {@code search.*} configuration namespace. This is the entire
 * plug-and-play config surface described in the project spec: point
 * {@code search.datasource.type} at an adapter, fill in that adapter's
 * block, done.
 * <p>
 * Uses constructor binding (records) throughout, per current Spring Boot
 * conventions - each nested record supplies its own defaults via a compact
 * constructor so partially-specified YAML still binds cleanly.
 */
@ConfigurationProperties(prefix = "search")
public record SearchProperties(
        Datasource datasource,
        Postgres postgres,
        Mysql mysql,
        Hybrid hybrid,
        Mcp mcp
) {

    public SearchProperties {
        datasource = datasource != null ? datasource : new Datasource(null);
        postgres = postgres != null ? postgres : new Postgres(null, null, null, null, null, null, null, null, null, null);
        mysql = mysql != null ? mysql : new Mysql(null, null, null, null, null, null, null);
        hybrid = hybrid != null ? hybrid : new Hybrid(null, null, null);
        mcp = mcp != null ? mcp : new Mcp(null, null);
    }

    /** @param type which adapter family to activate, e.g. {@code "postgres"} or {@code "mysql"} */
    public record Datasource(String type) {
    }

    /**
     * Mirrors {@link io.mcpsearch.adapter.postgres.PostgresAdapterConfig}; kept as a
     * separate properties record (rather than binding that config type directly) so the
     * starter's config surface doesn't have to change in lockstep with adapter internals.
     */
    public record Postgres(
            String table,
            String idColumn,
            String textSearchExpression,
            String tsvectorColumn,
            String vectorColumn,
            String language,
            List<String> selectColumns,
            Boolean exactTotalHits,
            Integer hnswEfSearch,
            Boolean hnswIterativeScan
    ) {
        public Postgres {
            idColumn = idColumn != null ? idColumn : "id";
            language = language != null ? language : "english";
            selectColumns = selectColumns != null ? selectColumns : List.of();
            // Exact counts are correct but scan everything; keep them on by default and let
            // large deployments turn them off explicitly.
            exactTotalHits = exactTotalHits != null ? exactTotalHits : true;
        }
    }

    /**
     * Mirrors {@link io.mcpsearch.adapter.mysql.MysqlAdapterConfig}. Note {@code textColumns}
     * (plural) rather than Postgres's single {@code textSearchExpression}: MySQL's
     * {@code MATCH ... AGAINST} must name columns covered by a {@code FULLTEXT} index, it
     * can't search an arbitrary expression. Requires MySQL 9.0+ when {@code vectorColumn} is set.
     */
    public record Mysql(
            String table,
            String idColumn,
            List<String> textColumns,
            String vectorColumn,
            String distanceMetric,
            List<String> selectColumns,
            Integer vectorCandidatePoolSize
    ) {
        public Mysql {
            idColumn = idColumn != null ? idColumn : "id";
            distanceMetric = distanceMetric != null ? distanceMetric : "COSINE";
            selectColumns = selectColumns != null ? selectColumns : List.of();
            // 0 lets the adapter apply its own default; see MysqlAdapterConfig.
            vectorCandidatePoolSize = vectorCandidatePoolSize != null ? vectorCandidatePoolSize : 0;
        }
    }

    /** Defaults applied to a query when the MCP tool caller doesn't override them. */
    public record Hybrid(String defaultMode, Double defaultAlpha, Integer defaultTopK) {
        public Hybrid {
            defaultMode = defaultMode != null ? defaultMode : "RRF";
            defaultAlpha = defaultAlpha != null ? defaultAlpha : 0.5;
            defaultTopK = defaultTopK != null ? defaultTopK : 10;
        }
    }

    /** Metadata for the exposed MCP tool itself. */
    public record Mcp(String toolName, String toolDescription) {
        public Mcp {
            toolName = toolName != null ? toolName : "search";
            toolDescription = toolDescription != null
                    ? toolDescription
                    : "Hybrid full-text and semantic search over the configured datastore.";
        }
    }
}
