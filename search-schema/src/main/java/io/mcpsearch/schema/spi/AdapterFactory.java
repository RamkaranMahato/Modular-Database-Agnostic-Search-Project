package io.mcpsearch.schema.spi;

import io.mcpsearch.core.spi.SearchAdapter;
import io.mcpsearch.schema.model.TableMetadata;

/**
 * Produces a {@link SearchAdapter} scoped to a single table. The orchestrator
 * selects tables abstractly (by metadata) and then needs a concrete adapter
 * per selected table to actually run the hybrid search; this factory is that
 * bridge.
 * <p>
 * I/O-adjacent edge: the production implementation builds e.g. a
 * {@code PostgresSearchAdapter} with a {@code PostgresAdapterConfig} derived
 * from the table's metadata, sharing one pooled {@code DataSource}. Adapters
 * are cheap wrappers over a shared connection pool, so per-call construction
 * is fine; an implementation may cache them per table if it prefers.
 */
public interface AdapterFactory {

    SearchAdapter forTable(TableMetadata table);
}
