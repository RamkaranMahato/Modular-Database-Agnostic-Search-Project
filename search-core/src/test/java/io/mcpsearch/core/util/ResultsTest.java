package io.mcpsearch.core.util;

import io.mcpsearch.core.model.SearchResult;
import io.mcpsearch.core.model.SortField;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultsTest {

    private static SearchResult result(String id, int rank, Object price) {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", id);
        if (price != null) {
            source.put("price", price);
        }
        return new SearchResult(id, 1.0 / rank, null, null, rank, source);
    }

    private static List<SearchResult> ten() {
        List<SearchResult> list = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            list.add(result("id" + i, i, i * 10.0));
        }
        return list;
    }

    private static List<String> ids(List<SearchResult> results) {
        return results.stream().map(SearchResult::id).toList();
    }

    @Test
    void eachPageReturnsADifferentWindow() {
        List<SearchResult> all = ten();

        assertEquals(List.of("id1", "id2", "id3"), ids(Results.paginate(all, 0, 3)));
        assertEquals(List.of("id4", "id5", "id6"), ids(Results.paginate(all, 1, 3)));
        assertEquals(List.of("id7", "id8", "id9"), ids(Results.paginate(all, 2, 3)));
    }

    @Test
    void rankStaysGlobalAcrossPages() {
        assertEquals(4, Results.paginate(ten(), 1, 3).get(0).rank());
    }

    @Test
    void pagePastTheEndIsEmptyRatherThanThrowing() {
        assertTrue(Results.paginate(ten(), 99, 10).isEmpty());
    }

    @Test
    void lastPageMayBePartial() {
        assertEquals(List.of("id10"), ids(Results.paginate(ten(), 3, 3)));
    }

    @Test
    void sortAscendingAndDescendingByField() {
        assertEquals("id1", Results.applySort(ten(), List.of(SortField.asc("price"))).get(0).id());
        assertEquals("id10", Results.applySort(ten(), List.of(SortField.desc("price"))).get(0).id());
    }

    @Test
    void noSortFieldsPreservesRelevanceOrder() {
        assertEquals(ids(ten()), ids(Results.applySort(ten(), List.of())));
    }

    @Test
    void rowsMissingTheSortFieldGoLast() {
        List<SearchResult> mixed = List.of(result("has", 1, 50.0), result("missing", 2, null));

        List<SearchResult> sorted = Results.applySort(mixed, List.of(SortField.asc("price")));

        assertEquals("has", sorted.get(0).id());
        assertEquals("missing", sorted.get(1).id());
    }

    @Test
    void sortAppliesBeforePagingNotWithinIt() {
        List<SearchResult> sorted = Results.applySort(ten(), List.of(SortField.desc("price")));

        assertEquals(List.of("id10", "id9", "id8"), ids(Results.paginate(sorted, 0, 3)));
    }
}
