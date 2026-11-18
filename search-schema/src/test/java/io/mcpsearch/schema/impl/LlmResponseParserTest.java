package io.mcpsearch.schema.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmResponseParserTest {

    @Test
    void parsesBareJsonArray() {
        assertEquals(List.of("products", "orders"),
                LlmResponseParser.parseTableNames("[\"products\", \"orders\"]"));
    }

    @Test
    void parsesArrayEmbeddedInProseAndFences() {
        String response = "Sure! Here are the tables:\n```json\n[\"products\", \"return_policies\"]\n```\nHope that helps.";

        assertEquals(List.of("products", "return_policies"),
                LlmResponseParser.parseTableNames(response));
    }

    @Test
    void parsesSingleQuotedNames() {
        assertEquals(List.of("a", "b"), LlmResponseParser.parseTableNames("['a', 'b']"));
    }

    @Test
    void deduplicatesPreservingFirstSeenOrder() {
        assertEquals(List.of("x", "y"), LlmResponseParser.parseTableNames("[\"x\", \"y\", \"x\"]"));
    }

    @Test
    void returnsEmptyWhenNoArrayPresent() {
        assertEquals(List.of(), LlmResponseParser.parseTableNames("no array here"));
        assertEquals(List.of(), LlmResponseParser.parseTableNames(null));
        assertEquals(List.of(), LlmResponseParser.parseTableNames("[]"));
    }
}
