package io.mcpsearch.schema.impl;

import io.mcpsearch.core.exception.EmbeddingGenerationException;
import io.mcpsearch.core.spi.EmbeddingProvider;
import io.mcpsearch.schema.model.SchemaMetadata;
import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.orchestrate.SchemaSearchConfig;
import io.mcpsearch.schema.orchestrate.SchemaSearchOrchestrator;
import io.mcpsearch.schema.select.TableEmbeddingIndex;
import io.mcpsearch.schema.select.TableSelector;
import io.mcpsearch.schema.spi.AdapterFactory;
import io.mcpsearch.schema.spi.SchemaProvider;
import io.mcpsearch.schema.spi.TableRefiner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-time startup assembler for the whole-schema search pipeline. Ties the
 * SPIs together into a ready-to-use {@link SchemaSearchOrchestrator}:
 * <ol>
 *   <li>load the schema ({@link SchemaProvider});</li>
 *   <li>keep the searchable tables, optionally intersected with a config
 *       allow-list;</li>
 *   <li>embed each searchable table's metadata once
 *       ({@link EmbeddingProvider} over {@link TableMetadata#toEmbeddingText()})
 *       to build the {@link TableEmbeddingIndex};</li>
 *   <li>assemble the {@link TableSelector} and {@link SchemaSearchOrchestrator}.</li>
 * </ol>
 * The embedding of table metadata here is the one startup-time I/O cost; it's
 * paid once, not per query. Call {@link #build} at application startup and
 * hold the returned orchestrator.
 */
public final class SchemaSearchFactory {

    private SchemaSearchFactory() {
    }

    /**
     * @param schemaProvider    reads the schema (I/O edge)
     * @param embeddingProvider embeds table metadata and, later, queries (I/O edge)
     * @param refiner           optional LLM refine stage; may be {@code null}
     * @param adapterFactory    builds per-table adapters (I/O edge)
     * @param config            pipeline tuning
     * @param allowList         if non-empty, restrict searchable tables to these names
     *                          (the "auto-discover, overridable in config" knob); empty = all searchable
     * @throws EmbeddingGenerationException if embedding any table's metadata fails at startup
     */
    public static SchemaSearchOrchestrator build(
            SchemaProvider schemaProvider,
            EmbeddingProvider embeddingProvider,
            TableRefiner refiner,
            AdapterFactory adapterFactory,
            SchemaSearchConfig config,
            Set<String> allowList
    ) throws EmbeddingGenerationException {

        SchemaMetadata schema = schemaProvider.loadSchema();

        List<TableMetadata> searchable = schema.searchableTables().stream()
                .filter(t -> allowList.isEmpty() || allowList.contains(t.name()))
                .collect(java.util.stream.Collectors.toMap(
                        TableMetadata::name,
                        java.util.function.Function.identity(),
                        (first, duplicate) -> first,   // keep first, consistent with SchemaMetadata.byName()
                        LinkedHashMap::new))
                .values().stream().toList();

        // Embed each searchable table's metadata once, in a batch where possible.
        List<String> texts = searchable.stream().map(TableMetadata::toEmbeddingText).toList();
        List<float[]> embeddings = embeddingProvider.embedBatch(texts);

        Map<TableMetadata, float[]> tableEmbeddings = new LinkedHashMap<>();
        for (int i = 0; i < searchable.size(); i++) {
            tableEmbeddings.put(searchable.get(i), embeddings.get(i));
        }

        TableEmbeddingIndex embeddingIndex = new TableEmbeddingIndex(tableEmbeddings);
        TableSelector selector = new TableSelector(embeddingIndex, refiner);

        return new SchemaSearchOrchestrator(schema, selector, embeddingProvider, adapterFactory, config);
    }
}
