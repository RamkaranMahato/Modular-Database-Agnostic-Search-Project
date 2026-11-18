package io.mcpsearch.starter;

import io.mcpsearch.adapter.postgres.PostgresAdapterConfig;
import io.mcpsearch.adapter.postgres.PostgresSearchAdapter;
import io.mcpsearch.adapter.mysql.MysqlAdapterConfig;
import io.mcpsearch.adapter.mysql.MysqlSearchAdapter;
import io.mcpsearch.core.spi.EmbeddingProvider;
import io.mcpsearch.core.spi.SearchAdapter;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Wires a {@link SearchAdapter} bean based on {@code search.datasource.type}.
 * This class - not the MCP tool layer - is the actual "plug and play"
 * mechanism: it's the only place that knows which adapter implementation
 * to construct, and it's the only thing a new adapter module needs to add
 * a {@code @Bean} method to.
 * <p>
 * {@code DataSource} itself is <em>not</em> configured here - it comes from
 * Spring Boot's own {@code DataSourceAutoConfiguration}, triggered by the
 * consuming application's normal {@code spring.datasource.*} properties and
 * driver dependency. This starter only adds the adapter-specific config on
 * top (table/column names, defaults) rather than reinventing connection
 * management.
 */
@AutoConfiguration
@EnableConfigurationProperties(SearchProperties.class)
public class SearchAutoConfiguration {

    @Bean
    @ConditionalOnClass(PostgresSearchAdapter.class)
    @ConditionalOnProperty(prefix = "search.datasource", name = "type", havingValue = "postgres")
    @ConditionalOnMissingBean(SearchAdapter.class)
    public SearchAdapter postgresSearchAdapter(
            DataSource dataSource,
            SearchProperties properties,
            ObjectProvider<EmbeddingProvider> embeddingProvider
    ) {
        SearchProperties.Postgres pg = properties.postgres();
        if (pg.table() == null || pg.textSearchExpression() == null) {
            throw new IllegalStateException(
                    "search.datasource.type=postgres requires search.postgres.table and "
                            + "search.postgres.text-search-expression to be set");
        }

        PostgresAdapterConfig.Builder configBuilder = PostgresAdapterConfig
                .builder(pg.table(), pg.idColumn(), pg.textSearchExpression())
                .tsvectorColumn(pg.tsvectorColumn())
                .vectorColumn(pg.vectorColumn())
                .language(pg.language())
                .exactTotalHits(pg.exactTotalHits())
                .hnswEfSearch(pg.hnswEfSearch())
                .hnswIterativeScan(pg.hnswIterativeScan());
        for (String column : pg.selectColumns()) {
            configBuilder.selectColumn(column);
        }

        // embeddingProvider is optional: if the consuming app hasn't defined one, HYBRID/VECTOR
        // queries must supply a precomputed vector directly (see PostgresSearchAdapter javadoc).
        return new PostgresSearchAdapter(dataSource, configBuilder.build(), embeddingProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnClass(MysqlSearchAdapter.class)
    @ConditionalOnProperty(prefix = "search.datasource", name = "type", havingValue = "mysql")
    @ConditionalOnMissingBean(SearchAdapter.class)
    public SearchAdapter mysqlSearchAdapter(
            DataSource dataSource,
            SearchProperties properties,
            ObjectProvider<EmbeddingProvider> embeddingProvider
    ) {
        SearchProperties.Mysql my = properties.mysql();
        if (my.table() == null || my.textColumns() == null || my.textColumns().isEmpty()) {
            throw new IllegalStateException(
                    "search.datasource.type=mysql requires search.mysql.table and "
                            + "search.mysql.text-columns to be set (and those columns must be covered "
                            + "by a FULLTEXT index)");
        }

        MysqlAdapterConfig.Builder configBuilder = MysqlAdapterConfig
                .builder(my.table(), my.idColumn(), my.textColumns())
                .vectorColumn(my.vectorColumn())
                .distanceMetric(my.distanceMetric())
                .vectorCandidatePoolSize(my.vectorCandidatePoolSize());
        for (String column : my.selectColumns()) {
            configBuilder.selectColumn(column);
        }

        return new MysqlSearchAdapter(dataSource, configBuilder.build(), embeddingProvider.getIfAvailable());
    }

    /**
     * Declares {@link SearchToolService} as a bean. It's annotated {@code @Component} too, but
     * a consuming app only component-scans its own package, not this starter's - so without
     * this explicit {@code @Bean} the tool service isn't available for injection (e.g. into the
     * app's {@code MethodToolCallbackProvider}). Auto-configuration is loaded regardless of the
     * app's scan base, which is why it belongs here. Guarded on a {@link SearchAdapter} existing,
     * since the tool is useless without one.
     */
    @Bean
    @ConditionalOnMissingBean(SearchToolService.class)
    public SearchToolService searchToolService(SearchAdapter searchAdapter, SearchProperties properties) {
        return new SearchToolService(searchAdapter, properties);
    }
}
