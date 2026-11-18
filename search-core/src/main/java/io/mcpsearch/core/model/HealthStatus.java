package io.mcpsearch.core.model;

/**
 * Result of {@link io.mcpsearch.core.spi.SearchAdapter#healthCheck()} - kept
 * deliberately minimal so it maps cleanly onto Spring Boot Actuator's
 * {@code Health} type in the starter module without pulling an Actuator
 * dependency into this dependency-free core module.
 */
public record HealthStatus(boolean up, String message) {

    public static HealthStatus healthy() {
        return new HealthStatus(true, "OK");
    }

    public static HealthStatus healthy(String message) {
        return new HealthStatus(true, message);
    }

    public static HealthStatus down(String message) {
        return new HealthStatus(false, message);
    }
}
