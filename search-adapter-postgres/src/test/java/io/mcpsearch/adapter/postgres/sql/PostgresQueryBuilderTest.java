package io.mcpsearch.adapter.postgres.sql;

import io.mcpsearch.adapter.postgres.PostgresAdapterConfig;
import io.mcpsearch.core.model.Document;
import io.mcpsearch.core.model.Filter;
import io.mcpsearch.core.model.FilterOperator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostgresQueryBuilderTest {

    private static PostgresAdapterConfig baseConfig() {
        return PostgresAdapterConfig.builder("products", "id", "title")
                .vectorColumn("embedding")
                .build();
    }

    @Test
    void textQueryReferencesConfiguredExpressionAndOrdersByRank() {
        PreparedSql prepared = PostgresQueryBuilder.buildTextQuery(baseConfig(), "wireless mouse", List.of(), 20);

        assertTrue(prepared.sql().contains("to_tsvector(?, title)"));
        assertTrue(prepared.sql().contains("COUNT(*) OVER() AS __total"));
        assertTrue(prepared.sql().contains("ORDER BY __rank DESC LIMIT ?"));
        assertEquals(7, prepared.params().size());
        assertEquals("wireless mouse", prepared.params().get(2));
        assertEquals(20, prepared.params().get(6));
    }

    @Test
    void vectorQueryUsesConfiguredColumnAndCosineOperator() {
        float[] vector = {0.1f, 0.2f, 0.3f};

        PreparedSql prepared = PostgresQueryBuilder.buildVectorQuery(baseConfig(), vector, List.of(), 20);

        assertTrue(prepared.sql().contains("\"embedding\" <=> ?::vector"));
        assertEquals("[0.1,0.2,0.3]", prepared.params().get(0));
        assertEquals(20, prepared.params().get(2));
    }

    @Test
    void vectorQueryWithoutConfiguredColumnThrows() {
        PostgresAdapterConfig noVector = PostgresAdapterConfig.builder("products", "id", "title").build();

        assertThrows(IllegalStateException.class,
                () -> PostgresQueryBuilder.buildVectorQuery(noVector, new float[]{1f}, List.of(), 10));
    }

    @Test
    void filtersAreAppendedInOrder() {
        List<Filter> filters = List.of(
                Filter.eq("category", "electronics"),
                Filter.gte("price", 10),
                Filter.range("stock", 1, 100)
        );

        PreparedSql prepared = PostgresQueryBuilder.buildTextQuery(baseConfig(), "mouse", filters, 20);

        assertTrue(prepared.sql().contains("\"category\" = ?"));
        assertTrue(prepared.sql().contains("\"price\" >= ?"));
        assertTrue(prepared.sql().contains("\"stock\" BETWEEN ? AND ?"));
        assertEquals(11, prepared.params().size()); // 6 base + 1 + 1 + 2 + 1 limit
    }

    @Test
    void upsertBuildsInsertOnConflictWithVectorCast() {
        Document doc = new Document("p1", Map.of("title", "Wireless Mouse"), new float[]{0.1f, 0.2f});

        PreparedSql prepared = PostgresQueryBuilder.buildUpsert(baseConfig(), doc);

        assertTrue(prepared.sql().startsWith("INSERT INTO \"products\""));
        assertTrue(prepared.sql().contains("ON CONFLICT (\"id\") DO UPDATE SET"));
        assertTrue(prepared.sql().contains("?::vector"));
        assertEquals("p1", prepared.params().get(0));
    }

    @Test
    void deleteBuildsExactStatement() {
        PreparedSql prepared = PostgresQueryBuilder.buildDelete(baseConfig(), "p1");

        assertEquals("DELETE FROM \"products\" WHERE \"id\" = ?", prepared.sql());
        assertEquals(List.of("p1"), prepared.params());
    }

    @Test
    void maliciousFilterFieldNameIsRejected() {
        Filter malicious = new Filter("id; DROP TABLE products;--", FilterOperator.EQ, "x");

        assertThrows(IllegalArgumentException.class,
                () -> PostgresQueryBuilder.buildTextQuery(baseConfig(), "q", List.of(malicious), 10));
    }
}
