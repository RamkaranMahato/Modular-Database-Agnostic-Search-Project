package io.mcpsearch.schema.select;

import io.mcpsearch.schema.model.ColumnKind;
import io.mcpsearch.schema.model.ColumnMetadata;
import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.spi.TableRefiner;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableSelectorTest {

    private static TableMetadata table(String name) {
        return new TableMetadata(name, List.of(
                new ColumnMetadata("id", "int", ColumnKind.ID, null),
                new ColumnMetadata("body", "text", ColumnKind.TEXT, null)), null);
    }

    private static final TableMetadata PRODUCTS = table("products");
    private static final TableMetadata ORDERS = table("orders");

    private static TableEmbeddingIndex index() {
        Map<TableMetadata, float[]> emb = new LinkedHashMap<>();
        emb.put(PRODUCTS, new float[]{1, 0});
        emb.put(ORDERS, new float[]{0.9f, 0.1f});
        return new TableEmbeddingIndex(emb);
    }

    @Test
    void hallucinatedTablesAreDropped() {
        TableRefiner hallucinating = (q, shortlist) -> List.of(PRODUCTS, table("SECRET_NOT_IN_SHORTLIST"));
        TableSelector selector = new TableSelector(index(), hallucinating);

        List<String> names = selector.select("q", new float[]{1, 0}, 10, 0.0)
                .stream().map(TableMetadata::name).toList();

        assertTrue(names.contains("products"));
        assertFalse(names.contains("SECRET_NOT_IN_SHORTLIST"));
    }

    @Test
    void refinerFailureFallsBackToShortlist() {
        TableRefiner throwing = (q, shortlist) -> {
            throw new RuntimeException("LLM down");
        };
        TableSelector selector = new TableSelector(index(), throwing);

        List<TableMetadata> selected = selector.select("q", new float[]{1, 0}, 10, 0.0);

        assertFalse(selected.isEmpty());
    }

    @Test
    void emptyRefineResultFallsBackToShortlist() {
        TableRefiner empty = (q, shortlist) -> List.of();
        TableSelector selector = new TableSelector(index(), empty);

        List<TableMetadata> selected = selector.select("q", new float[]{1, 0}, 10, 0.0);

        assertFalse(selected.isEmpty());
    }

    @Test
    void noRefinerReturnsShortlistDirectly() {
        TableSelector selector = new TableSelector(index(), null);

        List<TableMetadata> selected = selector.select("q", new float[]{1, 0}, 10, 0.0);

        assertEquals("products", selected.get(0).name());
    }
}
