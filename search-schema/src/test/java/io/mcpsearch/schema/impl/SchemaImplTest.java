package io.mcpsearch.schema.impl;

import io.mcpsearch.core.model.Document;
import io.mcpsearch.core.model.HealthStatus;
import io.mcpsearch.core.model.SearchQuery;
import io.mcpsearch.core.model.SearchResponse;
import io.mcpsearch.core.spi.SearchAdapter;
import io.mcpsearch.schema.model.ColumnKind;
import io.mcpsearch.schema.model.ColumnMetadata;
import io.mcpsearch.schema.model.TableMetadata;
import io.mcpsearch.schema.spi.AdapterFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaImplTest {

    private static TableMetadata table(String name) {
        return new TableMetadata(name,
                List.of(new ColumnMetadata("body", "text", ColumnKind.TEXT, null)), null);
    }

    @Test
    void refinerMapsNamesToMetadataAndDropsHallucinations() {
        List<TableMetadata> shortlist = List.of(table("products"), table("orders"));
        UnaryOperator<String> chat = prompt -> "[\"products\", \"GHOST_TABLE\"]";

        List<TableMetadata> refined = new LlmTableRefiner(chat).refine("q", shortlist);

        assertEquals(List.of("products"), refined.stream().map(TableMetadata::name).toList());
    }

    @Test
    void refinerReturnsEmptyOnUnparseableResponse() {
        List<TableMetadata> refined =
                new LlmTableRefiner(p -> "I couldn't decide").refine("q", List.of(table("products")));

        assertEquals(List.of(), refined);
    }

    @Test
    void cachingFactoryBuildsOncePerDistinctTable() {
        AtomicInteger builds = new AtomicInteger();
        SearchAdapter dummy = new SearchAdapter() {
            public SearchResponse search(SearchQuery q) {
                return SearchResponse.empty(0, 10, 0);
            }

            public void index(Document d) {
            }

            public void delete(String id) {
            }

            public HealthStatus healthCheck() {
                return HealthStatus.healthy();
            }
        };
        AdapterFactory factory = new CachingAdapterFactory(t -> {
            builds.incrementAndGet();
            return dummy;
        });

        factory.forTable(table("products"));
        factory.forTable(table("products"));
        factory.forTable(table("orders"));

        assertEquals(2, builds.get());
    }
}
