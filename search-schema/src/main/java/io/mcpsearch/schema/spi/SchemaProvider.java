package io.mcpsearch.schema.spi;

import io.mcpsearch.schema.model.SchemaMetadata;

/**
 * Supplies the {@link SchemaMetadata} the whole module works over. This is
 * an I/O edge: the production implementation reads {@code information_schema}
 * (and pg_catalog for comments) from a live database, so it can't be
 * exercised in a sandbox without one. Kept behind this interface precisely
 * so everything downstream - the graph, the selector, the fusion - can be
 * tested against a hand-built {@link SchemaMetadata} instead.
 */
public interface SchemaProvider {

    /** Read the full schema. Called once at startup and cached by the caller. */
    SchemaMetadata loadSchema();
}
