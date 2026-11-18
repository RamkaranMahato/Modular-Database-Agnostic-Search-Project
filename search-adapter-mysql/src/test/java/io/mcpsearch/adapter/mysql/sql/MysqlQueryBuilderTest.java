package io.mcpsearch.adapter.mysql.sql;

import io.mcpsearch.adapter.mysql.MysqlAdapterConfig;
import io.mcpsearch.core.model.Document;
import io.mcpsearch.core.model.Filter;
import io.mcpsearch.core.model.FilterOperator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlQueryBuilderTest {

    private static MysqlAdapterConfig config() {
        return MysqlAdapterConfig.builder("products", "id", List.of("title", "body"))
                .vectorColumn("embedding")
                .build();
    }

    @Test
    void textQueryUsesMatchAgainstInSelectAndWhere() {
        PreparedSql p = MysqlQueryBuilder.buildTextQuery(config(), "wireless mouse", List.of(), 20);

        assertTrue(p.sql().contains("MATCH(`title`, `body`) AGAINST(? IN NATURAL LANGUAGE MODE) AS __rank"));
        assertTrue(p.sql().contains("WHERE MATCH(`title`, `body`) AGAINST(? IN NATURAL LANGUAGE MODE)"));
        assertTrue(p.sql().contains("ORDER BY __rank DESC LIMIT ?"));
        assertEquals(3, p.params().size());
        assertEquals("wireless mouse", p.params().get(0));
    }

    @Test
    void vectorCandidateQueryFetchesVectorAsStringAndFiltersNulls() {
        PreparedSql p = MysqlQueryBuilder.buildVectorCandidateQuery(config(), List.of(), 20);

        assertTrue(p.sql().contains("VECTOR_TO_STRING(`embedding`) AS __vector"));
        assertTrue(p.sql().contains("`embedding` IS NOT NULL"));
        assertTrue(p.sql().contains("LIMIT ?"));
        assertEquals(20, p.params().get(p.params().size() - 1));
    }

    @Test
    void upsertUsesRowAliasFormNotDeprecatedValues() {
        Document doc = new Document("p1", Map.of("title", "Mouse"), new float[]{0.1f, 0.2f});

        PreparedSql p = MysqlQueryBuilder.buildUpsert(config(), doc);

        assertTrue(p.sql().contains(") AS new ON DUPLICATE KEY UPDATE"));
        assertTrue(p.sql().contains("= new.`title`"));
        assertTrue(p.sql().contains("STRING_TO_VECTOR(?)"));
    }

    @Test
    void deleteBuildsExactStatement() {
        PreparedSql p = MysqlQueryBuilder.buildDelete(config(), "p1");

        assertEquals("DELETE FROM `products` WHERE `id` = ?", p.sql());
        assertEquals(List.of("p1"), p.params());
    }

    @Test
    void vectorQueryWithoutColumnThrows() {
        MysqlAdapterConfig noVector = MysqlAdapterConfig.builder("t", "id", List.of("body")).build();

        assertThrows(IllegalStateException.class,
                () -> MysqlQueryBuilder.buildVectorCandidateQuery(noVector, List.of(), 10));
    }

    @Test
    void maliciousFilterFieldIsRejected() {
        Filter malicious = new Filter("id`; DROP TABLE products;--", FilterOperator.EQ, "x");

        assertThrows(IllegalArgumentException.class,
                () -> MysqlQueryBuilder.buildTextQuery(config(), "q", List.of(malicious), 10));
    }
}
