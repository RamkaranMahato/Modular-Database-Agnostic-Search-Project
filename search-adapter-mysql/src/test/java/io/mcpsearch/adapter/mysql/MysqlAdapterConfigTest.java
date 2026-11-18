package io.mcpsearch.adapter.mysql;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysqlAdapterConfigTest {

    @Test
    void requiresAtLeastOneTextColumn() {
        assertThrows(IllegalArgumentException.class,
                () -> MysqlAdapterConfig.builder("t", "id", List.of()).build());
    }

    @Test
    void rejectsUnsupportedDistanceMetric() {
        assertThrows(IllegalArgumentException.class,
                () -> MysqlAdapterConfig.builder("t", "id", List.of("body")).distanceMetric("MANHATTAN").build());
    }

    @Test
    void normalizesMetricToUpperCase() {
        MysqlAdapterConfig config = MysqlAdapterConfig.builder("t", "id", List.of("body"))
                .distanceMetric("cosine").build();

        assertEquals("COSINE", config.distanceMetric());
    }

    @Test
    void rejectsMaliciousTableName() {
        assertThrows(IllegalArgumentException.class,
                () -> MysqlAdapterConfig.builder("products`; DROP", "id", List.of("body")).build());
    }

    @Test
    void defaultsMetricToCosine() {
        MysqlAdapterConfig config = MysqlAdapterConfig.builder("t", "id", List.of("body")).build();

        assertEquals("COSINE", config.distanceMetric());
    }
}
