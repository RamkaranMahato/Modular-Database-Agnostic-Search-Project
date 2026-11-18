package io.mcpsearch.schema.orchestrate;

import io.mcpsearch.core.exception.EmbeddingGenerationException;
import io.mcpsearch.core.exception.SearchException;
import io.mcpsearch.core.model.SearchMode;
import io.mcpsearch.core.model.SearchQuery;
import io.mcpsearch.core.model.SearchResponse;
import io.mcpsearch.core.model.SearchResult;
import io.mcpsearch.core.spi.EmbeddingProvider;
import io.mcpsearch.core.spi.SearchAdapter;
import io.mcpsearch.schema.graph.SchemaGraph;
import io.mcpsearch.schema.model.SchemaMetadata;
import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.select.TableSelector;
import io.mcpsearch.schema.spi.AdapterFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The whole-schema search pipeline, end to end:
 * <ol>
 *   <li>embed the query;</li>
 *   <li>select query-relevant tables (embedding shortlist -&gt; optional LLM refine),
 *       via {@link TableSelector};</li>
 *   <li>expand the selection along foreign keys, via {@link SchemaGraph};</li>
 *   <li>run hybrid search against each selected table, via a per-table
 *       {@link SearchAdapter} from {@link AdapterFactory};</li>
 *   <li>fuse the per-table results into one ranked list, via {@link CrossTableFusion}.</li>
 * </ol>
 * The wiring here is thin; the judgement-heavy logic lives in the collaborators,
 * each independently tested. The two genuinely external dependencies - the
 * {@link EmbeddingProvider} and whatever the {@link AdapterFactory} talks to -
 * are injected, so this class's own control flow (including the per-table
 * failure isolation below) can be tested with fakes.
 */
public final class SchemaSearchOrchestrator {

    private static final System.Logger LOGGER = System.getLogger(SchemaSearchOrchestrator.class.getName());

    private final SchemaMetadata schema;
    private final SchemaGraph graph;
    private final TableSelector tableSelector;
    private final EmbeddingProvider embeddingProvider;
    private final AdapterFactory adapterFactory;
    private final CrossTableFusion fusion;
    private final SchemaSearchConfig config;

    public SchemaSearchOrchestrator(
            SchemaMetadata schema,
            TableSelector tableSelector,
            EmbeddingProvider embeddingProvider,
            AdapterFactory adapterFactory,
            SchemaSearchConfig config
    ) {
        this.schema = schema;
        this.graph = new SchemaGraph(schema);
        this.tableSelector = tableSelector;
        this.embeddingProvider = embeddingProvider;
        this.adapterFactory = adapterFactory;
        this.fusion = new CrossTableFusion(config.rrfK());
        this.config = config;
    }

    public List<TableScopedResult> search(String queryText) throws SearchException {
        float[] queryEmbedding;
        try {
            queryEmbedding = embeddingProvider.embed(queryText);
        } catch (EmbeddingGenerationException e) {
            throw new SearchException("Failed to embed query for table selection", e);
        }

        // Stage 1-2: pick query-relevant tables.
        List<TableMetadata> selected = tableSelector.select(
                queryText, queryEmbedding, config.shortlistSize(), config.minSimilarity());
        if (selected.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Schema search selected NO tables for query \"{0}\" - every candidate scored below "
                            + "minSimilarity={1}. Lower minSimilarity, or improve table/column naming and "
                            + "comments (table selection matches against table metadata text, not row data).",
                    queryText, config.minSimilarity());
            return List.of();
        }
        LOGGER.log(System.Logger.Level.INFO, "Schema search selected tables: {0}",
                selected.stream().map(TableMetadata::name).toList());

        // Stage 3: expand along foreign keys, then keep only searchable tables.
        Set<String> seedNames = new LinkedHashSet<>();
        selected.forEach(t -> seedNames.add(t.name()));
        Set<String> expanded = graph.expand(seedNames, config.fkDirection(), config.fkDepth(), config.maxTables());

        Map<String, TableMetadata> byName = schema.byName();
        List<TableMetadata> tablesToSearch = new ArrayList<>();
        for (String name : expanded) {
            TableMetadata table = byName.get(name);
            // An FK-expanded neighbour is only worth searching if it's itself searchable;
            // a pure join table pulled in by expansion has nothing to match against.
            if (table != null && table.isSearchable()) {
                tablesToSearch.add(table);
            }
        }

        // Stage 4: hybrid search each selected table. One table failing (missing
        // vector column, transient error) must not sink the whole query - isolate it.
        Map<String, List<SearchResult>> perTableResults = new LinkedHashMap<>();
        for (TableMetadata table : tablesToSearch) {
            try {
                SearchAdapter adapter = adapterFactory.forTable(table);
                SearchQuery query = SearchQuery.builder()
                        .text(queryText)
                        .mode(SearchMode.HYBRID)
                        .size(config.topK())
                        .build();
                SearchResponse response = adapter.search(query);
                if (!response.results().isEmpty()) {
                    perTableResults.put(table.name(), response.results());
                }
            } catch (SearchException | RuntimeException e) {
                // Skip this table, keep going - a partial result beats a failed search. But say so:
                // swallowing this silently turns a real error into a mysterious empty result set.
                perTableResults.remove(table.name());
                LOGGER.log(System.Logger.Level.WARNING,
                        "Schema search: table `" + table.name() + "` failed and was skipped: " + e.getMessage(), e);
            }
        }

        if (perTableResults.isEmpty()) {
            LOGGER.log(System.Logger.Level.WARNING,
                    "Schema search: {0} table(s) were searched but none returned results. If warnings above "
                            + "show failures, fix those; otherwise the query genuinely matched nothing.",
                    tablesToSearch.size());
        }

        // Stage 5: fuse across tables.
        return fusion.fuse(perTableResults, config.topK());
    }
}
