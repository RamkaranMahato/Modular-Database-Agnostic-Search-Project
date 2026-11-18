package io.mcpsearch.schema.graph;

import io.mcpsearch.schema.model.ForeignKey;
import io.mcpsearch.schema.model.SchemaMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaGraphTest {

    private static SchemaGraph graph() {
        List<ForeignKey> fks = List.of(
                new ForeignKey("orders", "customer_id", "customers", "id"),
                new ForeignKey("orders", "product_id", "products", "id"),
                new ForeignKey("reviews", "product_id", "products", "id"));
        return new SchemaGraph(new SchemaMetadata(List.of(), fks));
    }

    @Test
    void outgoingReachesReferencedTables() {
        Set<String> result = graph().expand(Set.of("orders"), Direction.OUTGOING, 1, 100);

        assertTrue(result.contains("customers"));
        assertTrue(result.contains("products"));
        assertFalse(result.contains("reviews"));
    }

    @Test
    void incomingReachesReferencingTables() {
        Set<String> result = graph().expand(Set.of("products"), Direction.INCOMING, 1, 100);

        assertTrue(result.contains("orders"));
        assertTrue(result.contains("reviews"));
        assertFalse(result.contains("customers"));
    }

    @Test
    void depthZeroReturnsSeedsOnly() {
        Set<String> result = graph().expand(Set.of("orders"), Direction.OUTGOING, 0, 100);

        assertTrue(result.contains("orders"));
        assertFalse(result.contains("customers"));
    }

    @Test
    void capNeverDropsSeeds() {
        Set<String> result = graph().expand(
                Set.of("orders", "reviews", "customers"), Direction.BOTH, 2, 1);

        assertTrue(result.containsAll(Set.of("orders", "reviews", "customers")));
    }

    @Test
    void capLimitsExpansion() {
        Set<String> result = graph().expand(Set.of("orders"), Direction.BOTH, 3, 2);

        assertTrue(result.size() <= 2);
    }
}
