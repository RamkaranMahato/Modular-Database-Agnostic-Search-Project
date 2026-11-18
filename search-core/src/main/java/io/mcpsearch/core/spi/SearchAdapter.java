package io.mcpsearch.core.spi;

import io.mcpsearch.core.exception.SearchException;
import io.mcpsearch.core.model.Document;
import io.mcpsearch.core.model.HealthStatus;
import io.mcpsearch.core.model.SearchQuery;
import io.mcpsearch.core.model.SearchResponse;

/**
 * The single seam every datastore integration implements. This is the whole
 * "plug and play" contract: a consuming project selects one adapter via
 * config, and everything above this interface (MCP tool exposure, fusion,
 * query validation) is identical regardless of which one it is.
 * <p>
 * Implementations are expected to be thread-safe and stateless beyond a
 * connection/client held internally - the starter module manages their
 * lifecycle as a singleton Spring bean.
 * <p>
 * For {@link io.mcpsearch.core.model.SearchMode#HYBRID} queries, an adapter
 * that talks to an engine with native hybrid support (e.g. Elasticsearch)
 * may run fusion itself and return an already-fused response. An adapter
 * without native support should run both channels and fuse using a
 * {@link FusionStrategy} obtained from
 * {@link io.mcpsearch.core.fusion.FusionStrategyFactory}. Either way the
 * caller sees the same {@link SearchResponse} shape.
 */
public interface SearchAdapter {

    /**
     * Execute a search.
     *
     * @throws SearchException if the underlying datastore call fails
     */
    SearchResponse search(SearchQuery query) throws SearchException;

    /**
     * Index (insert or upsert) a single document. Bulk indexing is
     * intentionally out of scope for this interface - implementations that
     * support it can expose it as an additional, adapter-specific method;
     * this contract only guarantees the single-document path every adapter
     * must have.
     *
     * @throws SearchException if the underlying datastore call fails
     */
    void index(Document document) throws SearchException;

    /**
     * Remove a document by id. Implementations should treat deleting a
     * non-existent id as a no-op, not an error.
     *
     * @throws SearchException if the underlying datastore call fails
     */
    void delete(String id) throws SearchException;

    /**
     * Cheap connectivity/readiness check - no heavy queries. Used by the
     * starter module's Actuator health indicator and by the MCP server's
     * own startup validation. Must not throw; report failure via
     * {@link HealthStatus#down(String)} instead.
     */
    HealthStatus healthCheck();
}
