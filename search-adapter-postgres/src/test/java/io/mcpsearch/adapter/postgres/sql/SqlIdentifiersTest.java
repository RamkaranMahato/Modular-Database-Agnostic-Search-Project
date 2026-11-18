package io.mcpsearch.adapter.postgres.sql;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlIdentifiersTest {

    @Test
    void validIdentifierPasses() {
        assertDoesNotThrow(() -> SqlIdentifiers.validate("product_name", "field"));
    }

    @Test
    void identifierWithSpaceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifiers.validate("product name", "field"));
    }

    @Test
    void identifierWithSqlInjectionAttemptIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlIdentifiers.validate("id; DROP TABLE products;--", "field"));
    }

    @Test
    void identifierStartingWithDigitIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifiers.validate("1column", "field"));
    }

    @Test
    void nullIdentifierIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> SqlIdentifiers.validate(null, "field"));
    }

    @Test
    void quoteWrapsInDoubleQuotes() {
        org.junit.jupiter.api.Assertions.assertEquals("\"id\"", SqlIdentifiers.quote("id"));
    }
}
