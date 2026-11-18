package io.mcpsearch.app;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Config for {@link EmbeddingBackfillRunner}, under {@code app.backfill.*}.
 * Defaults target the MySQL {@code products} demo table.
 * <p>
 * Registered via {@code @EnableConfigurationProperties} in {@code SearchServerConfig},
 * NOT via {@code @Component} - a constructor-bound properties record can't be a regular
 * bean (Spring would try to autowire its constructor args as beans).
 *
 * @param enabled      master switch - the runner only exists as a bean when true
 * @param stopAfter    exit the JVM once the backfill finishes (default true, since this is a
 *                     one-shot utility you don't want holding a server open)
 * @param table        table to backfill
 * @param idColumn     primary key column
 * @param vectorColumn the VECTOR column to populate
 * @param textColumns  text columns concatenated to form the text that gets embedded
 * @param batchSize    how many rows to embed per API call (default 32); OpenAI's embeddings
 *                     endpoint accepts many inputs per request, so batching cuts round-trips sharply
 */
@ConfigurationProperties(prefix = "app.backfill")
public record BackfillProperties(
        Boolean enabled,
        Boolean stopAfter,
        String table,
        String idColumn,
        String vectorColumn,
        List<String> textColumns,
        Integer batchSize
) {
    public BackfillProperties {
        enabled = enabled != null ? enabled : false;
        stopAfter = stopAfter != null ? stopAfter : true;
        table = table != null ? table : "products";
        idColumn = idColumn != null ? idColumn : "id";
        vectorColumn = vectorColumn != null ? vectorColumn : "embedding";
        textColumns = (textColumns != null && !textColumns.isEmpty()) ? textColumns : List.of("title", "body");
        batchSize = (batchSize != null && batchSize > 0) ? batchSize : 32;
    }
}
