package io.mcpsearch.adapter.postgres.sql;

import io.mcpsearch.core.model.Filter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilterSqlRendererTest {

    @Test
    void emptyFilterListRendersEmptyString() {
        assertEquals("", FilterSqlRenderer.render(List.of(), new ArrayList<>()));
    }

    @Test
    void emptyInClauseRendersAlwaysFalse() {
        List<Object> params = new ArrayList<>();

        String fragment = FilterSqlRenderer.render(List.of(Filter.in("category", List.of())), params);

        assertTrue(fragment.contains("1=0"));
        assertTrue(params.isEmpty());
    }

    @Test
    void emptyNotInClauseRendersAlwaysTrue() {
        List<Object> params = new ArrayList<>();

        String fragment = FilterSqlRenderer.render(List.of(Filter.notIn("category", List.of())), params);

        assertTrue(fragment.contains("1=1"));
    }

    @Test
    void containsRendersIlikeWithWildcards() {
        List<Object> params = new ArrayList<>();

        String fragment = FilterSqlRenderer.render(List.of(Filter.contains("title", "mouse")), params);

        assertTrue(fragment.contains("ILIKE ?"));
        assertEquals("%mouse%", params.get(0));
    }

    @Test
    void multipleFiltersAreJoinedWithAnd() {
        List<Object> params = new ArrayList<>();

        String fragment = FilterSqlRenderer.render(
                List.of(Filter.eq("category", "books"), Filter.gt("price", 5)), params);

        assertTrue(fragment.contains(" AND "));
        assertEquals(2, params.size());
    }
}
