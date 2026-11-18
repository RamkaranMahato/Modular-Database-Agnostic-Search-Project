package io.mcpsearch.core.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SearchQueryTest {

    @Test
    void hybridModeRequiresText() {
        assertThrows(IllegalStateException.class,
                () -> SearchQuery.builder().mode(SearchMode.HYBRID).build());
    }

    @Test
    void vectorModeRequiresTextOrVector() {
        // Neither text nor vector -> invalid.
        assertThrows(IllegalStateException.class,
                () -> SearchQuery.builder().mode(SearchMode.VECTOR).build());
    }

    @Test
    void vectorModeAcceptsTextToEmbed() {
        // Text alone is fine for VECTOR mode - an adapter with an EmbeddingProvider embeds it.
        SearchQuery q = SearchQuery.builder().text("hello").mode(SearchMode.VECTOR).build();
        assertEquals(SearchMode.VECTOR, q.mode());
    }

    @Test
    void defaultsAreHybridWithRrf() {
        SearchQuery query = SearchQuery.builder().text("hello world").build();

        assertEquals(SearchMode.HYBRID, query.mode());
        assertEquals(FusionMode.RRF, query.fusionMode());
        assertEquals(0, query.page());
        assertEquals(10, query.size());
    }

    @Test
    void fusionConfigFallsBackToDefaultAlphaWhenUnset() {
        SearchQuery query = SearchQuery.builder().text("hello").build();

        assertEquals(0.5, query.fusionConfig().alpha(), 1e-9);
        assertEquals(60, query.fusionConfig().rrfK());
    }

    @Test
    void explicitAlphaOverridesDefault() {
        SearchQuery query = SearchQuery.builder().text("hello").alpha(0.8).build();

        assertEquals(0.8, query.fusionConfig().alpha(), 1e-9);
    }

    @Test
    void negativePageIsRejected() {
        assertThrows(IllegalStateException.class,
                () -> SearchQuery.builder().text("hello").page(-1).build());
    }
}
