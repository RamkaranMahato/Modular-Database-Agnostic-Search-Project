package io.mcpsearch.schema.impl;

import io.mcpsearch.schema.model.ColumnKind;
import io.mcpsearch.schema.model.ColumnMetadata;
import io.mcpsearch.schema.model.ForeignKey;
import io.mcpsearch.schema.model.SchemaMetadata;
import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.spi.SchemaProvider;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@link SchemaMetadata} from a live database via JDBC
 * {@link DatabaseMetaData} (which is backed by {@code information_schema} on
 * both Postgres and MySQL). Uses only {@code java.sql}/{@code javax.sql}, so
 * it builds with no driver dependency; the consuming app supplies the
 * {@code DataSource} and driver.
 * <p>
 * <strong>I/O edge - not exercised in the build sandbox.</strong> The
 * classification logic it relies on ({@link ColumnClassifier}) is unit-tested
 * separately; this class is the thin JDBC-walking shell around it and needs a
 * real database (or an integration test with Testcontainers) to verify
 * end to end. Reads are done once at startup and cached by the caller.
 *
 * @param dataSource the pooled data source to read metadata from
 * @param catalog    JDBC catalog (= database on MySQL) to scan. When {@code null} the provider
 *                   resolves the CONNECTION'S OWN catalog rather than passing null to JDBC -
 *                   passing null would mean "all catalogs" and scan every database on the server.
 * @param schemaPattern JDBC schema pattern to scan (e.g. {@code "public"} on Postgres),
 *                      or {@code null} for the default
 */
public record JdbcSchemaProvider(DataSource dataSource, String catalog, String schemaPattern)
        implements SchemaProvider {

    public JdbcSchemaProvider(DataSource dataSource) {
        this(dataSource, null, null);
    }

    @Override
    public SchemaMetadata loadSchema() {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            // CRITICAL: a null catalog in JDBC means "do not filter" - i.e. EVERY database on the
            // server, not the one you connected to. On MySQL that sweeps in sakila, world, etc.,
            // producing duplicate table names (two `city` tables) and merged column lists.
            // Default to the connection's own catalog so we only ever scan the current database.
            String effectiveCatalog = (catalog != null) ? catalog : conn.getCatalog();
            List<TableMetadata> tables = readTables(meta, effectiveCatalog);
            List<ForeignKey> foreignKeys = readForeignKeys(meta, effectiveCatalog, tables);
            return new SchemaMetadata(tables, foreignKeys);
        } catch (SQLException e) {
            throw new SchemaReadException("Failed to read schema metadata", e);
        }
    }

    private List<TableMetadata> readTables(DatabaseMetaData meta, String catalog) throws SQLException {
        List<String> tableNames = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tableNames.add(rs.getString("TABLE_NAME"));
            }
        }

        List<TableMetadata> tables = new ArrayList<>(tableNames.size());
        for (String tableName : tableNames) {
            Set<String> pkColumns = readPrimaryKeyColumns(meta, catalog, tableName);
            List<ColumnMetadata> columns = readColumns(meta, catalog, tableName, pkColumns);
            String comment = readTableComment(meta, catalog, tableName);
            tables.add(new TableMetadata(tableName, columns, comment));
        }
        return tables;
    }

    private Set<String> readPrimaryKeyColumns(DatabaseMetaData meta, String catalog, String tableName) throws SQLException {
        Set<String> pkColumns = new HashSet<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schemaPattern, tableName)) {
            while (rs.next()) {
                pkColumns.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pkColumns;
    }

    private List<ColumnMetadata> readColumns(DatabaseMetaData meta, String catalog, String tableName, Set<String> pkColumns)
            throws SQLException {
        List<ColumnMetadata> columns = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schemaPattern, tableName, "%")) {
            while (rs.next()) {
                String name = rs.getString("COLUMN_NAME");
                // TYPE_NAME carries the DB-specific type (e.g. "vector", "varchar", "text").
                String typeName = rs.getString("TYPE_NAME");
                String comment = rs.getString("REMARKS");
                ColumnKind kind = ColumnClassifier.classify(typeName, pkColumns.contains(name));
                columns.add(new ColumnMetadata(name, typeName == null ? "" : typeName, kind, comment));
            }
        }
        return columns;
    }

    private String readTableComment(DatabaseMetaData meta, String catalog, String tableName) throws SQLException {
        try (ResultSet rs = meta.getTables(catalog, schemaPattern, tableName, new String[]{"TABLE"})) {
            if (rs.next()) {
                return rs.getString("REMARKS");
            }
        }
        return null;
    }

    private List<ForeignKey> readForeignKeys(DatabaseMetaData meta, String catalog, List<TableMetadata> tables) throws SQLException {
        List<ForeignKey> foreignKeys = new ArrayList<>();
        for (TableMetadata table : tables) {
            try (ResultSet rs = meta.getImportedKeys(catalog, schemaPattern, table.name())) {
                while (rs.next()) {
                    foreignKeys.add(new ForeignKey(
                            rs.getString("FKTABLE_NAME"),
                            rs.getString("FKCOLUMN_NAME"),
                            rs.getString("PKTABLE_NAME"),
                            rs.getString("PKCOLUMN_NAME")));
                }
            }
        }
        return foreignKeys;
    }

    /** Unchecked wrapper so {@link SchemaProvider#loadSchema()} stays exception-signature-free. */
    public static final class SchemaReadException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public SchemaReadException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
