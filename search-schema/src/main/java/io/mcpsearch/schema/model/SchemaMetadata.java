package io.mcpsearch.schema.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The whole discovered schema: all tables plus all foreign keys. Built once
 * at startup by a {@link io.mcpsearch.schema.spi.SchemaProvider} and then
 * treated as immutable.
 */
public record SchemaMetadata(List<TableMetadata> tables, List<ForeignKey> foreignKeys) {

    public SchemaMetadata {
        tables = List.copyOf(tables);
        foreignKeys = List.copyOf(foreignKeys);
    }

    public List<TableMetadata> searchableTables() {
        return tables.stream().filter(TableMetadata::isSearchable).toList();
    }

    public Optional<TableMetadata> table(String name) {
        return tables.stream().filter(t -> t.name().equals(name)).findFirst();
    }

    /**
     * Index of tables by name, keeping the first occurrence on collision.
     * <p>
     * The merge function is not optional: {@code Collectors.toMap} throws on duplicate keys, and
     * duplicates are entirely possible - a JDBC scan that isn't scoped to one catalog/schema will
     * happily return two tables called {@code city} from different databases. Scoping the scan is
     * the real fix (see {@code JdbcSchemaProvider}); this keeps a stray duplicate from taking the
     * whole search down.
     */
    public Map<String, TableMetadata> byName() {
        return tables.stream().collect(Collectors.toMap(
                TableMetadata::name, Function.identity(), (first, duplicate) -> first));
    }
}
