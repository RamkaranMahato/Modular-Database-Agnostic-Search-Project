package io.mcpsearch.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterTest {

    @Test
    void inOperatorRequiresListValue() {
        assertThrows(IllegalArgumentException.class,
                () -> new Filter("category", FilterOperator.IN, "not-a-list"));
    }

    @Test
    void rangeOperatorRequiresExactlyTwoElements() {
        assertThrows(IllegalArgumentException.class,
                () -> new Filter("price", FilterOperator.RANGE, List.of(10)));
    }

    @Test
    void blankFieldIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Filter("  ", FilterOperator.EQ, "value"));
    }

    @Test
    void factoryMethodsSetOperatorCorrectly() {
        assertEquals(FilterOperator.EQ, Filter.eq("a", 1).operator());
        assertEquals(FilterOperator.RANGE, Filter.range("price", 10, 100).operator());
        assertEquals(FilterOperator.IN, Filter.in("category", List.of("a", "b")).operator());
    }
}
