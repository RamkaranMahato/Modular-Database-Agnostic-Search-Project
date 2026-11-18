package io.mcpsearch.schema.impl;

import io.mcpsearch.core.spi.SearchAdapter;
import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.spi.AdapterFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * An {@link AdapterFactory} that delegates the actual per-table adapter
 * construction to a caller-supplied function and caches the result per table.
 * <p>
 * Why a function rather than building the adapter here: {@code search-schema}
 * deliberately does not depend on {@code search-adapter-postgres} or
 * {@code search-adapter-mysql} - coupling the orchestration layer to specific
 * adapters would defeat the plug-and-play design. So the wiring layer (the
 * starter, or the standalone app) supplies a closure that knows how to turn a
 * {@link TableMetadata} into, say, a {@code PostgresSearchAdapter} with a
 * {@code PostgresAdapterConfig} derived from that table's columns. This class
 * just adds caching so we don't rebuild an adapter for every query.
 * <p>
 * The caching behaviour is pure and unit-tested with a counting fake; the
 * real per-table builder function is the I/O-adjacent edge, defined by the
 * wiring layer.
 */
public final class CachingAdapterFactory implements AdapterFactory {

    private final Function<TableMetadata, SearchAdapter> builder;
    private final Map<String, SearchAdapter> cache = new ConcurrentHashMap<>();

    public CachingAdapterFactory(Function<TableMetadata, SearchAdapter> builder) {
        this.builder = builder;
    }

    @Override
    public SearchAdapter forTable(TableMetadata table) {
        return cache.computeIfAbsent(table.name(), name -> builder.apply(table));
    }
}
