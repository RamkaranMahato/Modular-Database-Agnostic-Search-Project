package io.mcpsearch.app;

// Uses @Tool (not @McpTool) so the explicit MethodToolCallbackProvider in SearchServerConfig
// registers it - see the note in SearchToolService for why the @McpTool auto-scanner is avoided.
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import io.mcpsearch.core.exception.SearchException;
import io.mcpsearch.schema.orchestrate.SchemaSearchOrchestrator;
import io.mcpsearch.schema.orchestrate.TableScopedResult;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Exposes whole-schema search as a second MCP tool, {@code search_schema},
 * alongside the starter's single-table {@code search} tool. Only registered
 * when a {@link SchemaSearchOrchestrator} bean exists (i.e. when
 * {@code app.search.schema-search-enabled=true}), so a deployment that only
 * wants single-table search never sees it.
 * <p>
 * Returns each hit tagged with its origin table, since results span multiple
 * tables with different shapes.
 */
@Component
// Gated on the SAME property as SchemaSearchOrchestrator rather than @ConditionalOnBean.
// @ConditionalOnBean is only reliable inside auto-configuration classes: on a component-scanned
// @Component it is evaluated during scanning, typically BEFORE the @Configuration class has
// defined the orchestrator bean, so the condition silently fails and the tool never registers.
@ConditionalOnProperty(prefix = "app.search", name = "schema-search-enabled", havingValue = "true")
public class SchemaSearchToolService {

    private final SchemaSearchOrchestrator orchestrator;

    public SchemaSearchToolService(SchemaSearchOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @Tool(
            name = "search_schema",
            description = "Search across the whole database schema. Automatically selects the tables "
                    + "relevant to the query (and their foreign-key-related tables) and returns a single "
                    + "ranked list of results, each tagged with the table it came from.")
    public SchemaSearchToolResult searchSchema(
            @ToolParam(description = "The search query text") String query
    ) {
        try {
            List<TableScopedResult> results = orchestrator.search(query);
            List<SchemaSearchToolResult.Hit> hits = results.stream()
                    .map(r -> new SchemaSearchToolResult.Hit(
                            r.table(),
                            r.result().id(),
                            r.result().score(),
                            r.result().source()))
                    .toList();
            return new SchemaSearchToolResult(hits, hits.size());
        } catch (SearchException e) {
            throw new SchemaSearchToolException("Whole-schema search failed: " + e.getMessage(), e);
        }
    }

    /** JSON shape returned to the MCP client. */
    public record SchemaSearchToolResult(List<Hit> results, int count) {
        public record Hit(String table, String id, double score, Map<String, Object> source) {
        }
    }

    /** Unchecked wrapper so the tool method signals failure to Spring AI's invocation machinery. */
    public static final class SchemaSearchToolException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SchemaSearchToolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
