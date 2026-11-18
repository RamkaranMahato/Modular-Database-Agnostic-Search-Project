package io.mcpsearch.app;

import io.mcpsearch.adapter.mysql.MysqlAdapterConfig;
import io.mcpsearch.adapter.mysql.MysqlSearchAdapter;
import io.mcpsearch.core.spi.CachingEmbeddingProvider;
import io.mcpsearch.core.spi.EmbeddingProvider;
import io.mcpsearch.core.spi.RetryingEmbeddingProvider;
import io.mcpsearch.core.spi.SearchAdapter;
import io.mcpsearch.schema.impl.CachingAdapterFactory;
import io.mcpsearch.schema.impl.JdbcSchemaProvider;
import io.mcpsearch.schema.impl.LlmTableRefiner;
import io.mcpsearch.schema.impl.SchemaSearchFactory;
import io.mcpsearch.schema.model.ColumnMetadata;
import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.orchestrate.SchemaSearchConfig;
import io.mcpsearch.schema.orchestrate.SchemaSearchOrchestrator;
import io.mcpsearch.schema.spi.AdapterFactory;
import io.mcpsearch.schema.spi.SchemaProvider;
import io.mcpsearch.schema.spi.TableRefiner;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.mcpsearch.starter.SearchToolService;

import javax.sql.DataSource;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The small amount of hand-wiring this app adds on top of auto-configuration:
 * the embedding provider (bridging Spring AI to our SPI), and - if enabled -
 * the whole-schema search orchestrator with its concrete SPI implementations.
 * <p>
 * Everything here is deliberately thin glue. The heavy lifting is in modules
 * that were verified independently; this class just connects real Spring AI
 * models and a real {@code DataSource} to the interfaces those modules expect.
 */
@Configuration
@EnableConfigurationProperties({AppSearchProperties.class, BackfillProperties.class})
public class SearchServerConfig {

    private static final System.Logger LOG = System.getLogger(SearchServerConfig.class.getName());

    /**
     * The concrete {@link EmbeddingProvider}. Once this bean exists, the
     * starter's adapter will use it to embed queries automatically, and the
     * whole-schema factory will use it to embed table metadata at startup.
     */
    @Bean
    public EmbeddingProvider embeddingProvider(EmbeddingModel embeddingModel, AppSearchProperties props) {
        EmbeddingProvider provider =
                new SpringAiEmbeddingProvider(embeddingModel, props.embeddingModelName(), props.embeddingDimensions());

        // Decorator order is deliberate: retry wraps the real call, and cache wraps retry.
        // That way a cache hit costs nothing at all (it never enters the retry logic), while a
        // miss gets retried on transient failures and the successful result is then cached.
        // Inverting them would re-run the cache lookup on every retry attempt - pointless work.
        if (props.embeddingRetryEnabled()) {
            provider = new RetryingEmbeddingProvider(provider, props.embeddingRetryMaxAttempts(), 200);
        }
        if (props.embeddingCacheEnabled()) {
            provider = new CachingEmbeddingProvider(provider, props.embeddingCacheMaxEntries());
        }
        return provider;
    }

    /**
     * Explicitly registers the {@code @McpTool}-annotated beans with the MCP server.
     * <p>
     * Spring AI 2.0's annotation scanner is supposed to auto-register {@code @McpTool}
     * beans, but in practice it can silently find nothing ("No tool methods found in the
     * provided tool objects: []"), depending on the scanner property and proxying. Passing
     * the tool beans explicitly via {@link MethodToolCallbackProvider} is the reliable path
     * and is harmless even if the scanner also works - it's the single source of truth for
     * which beans expose tools.
     * <p>
     * {@code SchemaSearchToolService} is optional (only present when whole-schema search is
     * enabled), so it's injected via {@link ObjectProvider} and only added when available.
     */
    @Bean
    public ToolCallbackProvider mcpToolCallbackProvider(
            SearchToolService searchToolService,
            ObjectProvider<SchemaSearchToolService> schemaSearchToolService
    ) {
        SchemaSearchToolService schemaTool = schemaSearchToolService.getIfAvailable();
        Object[] toolObjects = (schemaTool != null)
                ? new Object[]{searchToolService, schemaTool}
                : new Object[]{searchToolService};
        return MethodToolCallbackProvider.builder()
                .toolObjects(toolObjects)
                .build();
    }

    // ----- optional whole-schema search (enabled via app.search.schema-search-enabled=true) -----

    @Bean
    @ConditionalOnProperty(prefix = "app.search", name = "schema-search-enabled", havingValue = "true")
    public SchemaProvider schemaProvider(DataSource dataSource) {
        // schemaPattern is DB-specific; null uses the connection default. For Postgres you'd
        // typically pass "public". For MySQL the schema is the database name, usually fine as null.
        return new JdbcSchemaProvider(dataSource);
    }

    /**
     * The LLM refine stage, only when both schema search AND refine are enabled.
     * Bridges Spring AI's {@link ChatClient} to the refiner's plain
     * {@code prompt -> completion} contract.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.search", name = "llm-refine-enabled", havingValue = "true")
    public TableRefiner tableRefiner(ObjectProvider<ChatClient.Builder> chatClientBuilderProvider) {
        // The ChatClient is resolved LAZILY, inside the lambda, rather than at bean-creation time.
        // This is load-bearing: Spring AI's chat model depends on the tool infrastructure
        // (toolCallbackResolver -> our ToolCallbackProvider -> schema tool -> orchestrator -> this
        // refiner), so touching the builder here would close a startup dependency cycle and the
        // context would refuse to start. The refiner doesn't need a chat client until a query
        // actually runs, by which point every bean exists.
        return new LlmTableRefiner(prompt -> chatClientBuilderProvider.getObject()
                .build()
                .prompt()
                .user(prompt)
                .call()
                .content());
    }

    /**
     * Builds per-table MySQL adapters for the whole-schema orchestrator. Derives a
     * {@link MysqlAdapterConfig} from each table's discovered metadata: all TEXT columns
     * feed the full-text channel, the VECTOR column (if any) feeds the semantic channel.
     * <p>
     * Assumes MySQL because that's this app's default datasource. A multi-datasource build
     * would switch on the discovered dialect here.
     */
    @Bean
    @ConditionalOnProperty(prefix = "app.search", name = "schema-search-enabled", havingValue = "true")
    public AdapterFactory adapterFactory(DataSource dataSource, EmbeddingProvider embeddingProvider) {
        Function<TableMetadata, SearchAdapter> builder = table -> {
            // MySQL requires MATCH(...) to name EXACTLY the columns of a declared FULLTEXT index.
            // Passing every text-typed column (e.g. adding `category` when the index is on
            // (title, body)) fails with "Can't find FULLTEXT index matching the column list".
            // So ask the database which columns its FULLTEXT index actually covers.
            List<String> textColumns = readFulltextIndexColumns(dataSource, table.name());
            if (textColumns.isEmpty()) {
                // No FULLTEXT index: fall back to the discovered text columns and let the adapter
                // fail loudly if MySQL rejects it - better than silently searching the wrong thing.
                textColumns = table.textColumns().stream().map(ColumnMetadata::name).toList();
            }
            if (textColumns.isEmpty()) {
                throw new IllegalStateException("table " + table.name() + " has no text columns to search");
            }
            String idColumn = table.idColumn().map(ColumnMetadata::name).orElse("id");
            MysqlAdapterConfig.Builder cfg = MysqlAdapterConfig.builder(table.name(), idColumn, textColumns);
            table.vectorColumn().ifPresent(v -> cfg.vectorColumn(v.name()));
            return new MysqlSearchAdapter(dataSource, cfg.build(), embeddingProvider);
        };
        return new CachingAdapterFactory(builder);
    }

    /**
     * Returns the columns of this table's first FULLTEXT index, in index order, or an empty list
     * if the table has none. MySQL-specific (reads information_schema.STATISTICS) and deliberately
     * kept here in the app wiring layer rather than in the DB-agnostic schema module.
     */
    private static List<String> readFulltextIndexColumns(DataSource dataSource, String tableName) {
        String sql = """
                SELECT INDEX_NAME, COLUMN_NAME
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_TYPE = 'FULLTEXT'
                ORDER BY INDEX_NAME, SEQ_IN_INDEX
                """;
        List<String> columns = new java.util.ArrayList<>();
        String firstIndex = null;
        try (java.sql.Connection conn = dataSource.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tableName);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String indexName = rs.getString("INDEX_NAME");
                    if (firstIndex == null) {
                        firstIndex = indexName;
                    }
                    // If several FULLTEXT indexes exist, use only the first - a MATCH list can
                    // correspond to one index, not a mixture.
                    if (firstIndex.equals(indexName)) {
                        columns.add(rs.getString("COLUMN_NAME"));
                    }
                }
            }
        } catch (java.sql.SQLException e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Could not read FULLTEXT index columns for `" + tableName + "`: " + e.getMessage());
            return List.of();
        }
        return columns;
    }

    @Bean
    @ConditionalOnProperty(prefix = "app.search", name = "schema-search-enabled", havingValue = "true")
    public SchemaSearchOrchestrator schemaSearchOrchestrator(
            SchemaProvider schemaProvider,
            EmbeddingProvider embeddingProvider,
            AdapterFactory adapterFactory,
            AppSearchProperties props,
            org.springframework.beans.factory.ObjectProvider<TableRefiner> tableRefiner
    ) throws Exception {
        // Refiner is optional even when schema search is on: only present if llm-refine-enabled=true.
        TableRefiner refiner = tableRefiner.getIfAvailable();
        SchemaSearchConfig config = new SchemaSearchConfig(
                props.schemaShortlistSize(),
                props.schemaMinSimilarity(),
                io.mcpsearch.schema.graph.Direction.OUTGOING,
                props.schemaFkDepth(),
                props.schemaMaxTables(),
                60,                       // RRF k - no reason to expose this one yet
                props.schemaTopK());
        return SchemaSearchFactory.build(
                schemaProvider,
                embeddingProvider,
                refiner,
                adapterFactory,
                config,
                Set.of()); // empty allow-list = search all auto-discovered searchable tables
    }
}
