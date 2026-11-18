package io.mcpsearch.schema.impl;

import io.mcpsearch.schema.model.TableMetadata;

import java.util.List;

/**
 * Builds the prompt handed to the LLM refiner. Separated from the LLM call
 * itself so the prompt content is unit-testable and easy to tune without
 * touching transport code.
 */
public final class RefinerPromptBuilder {

    private RefinerPromptBuilder() {
    }

    /**
     * Construct a prompt asking the model to pick the tables relevant to the
     * query. The instruction is deliberately strict about output format
     * (a JSON array, names only, drawn only from the given list) because
     * {@link LlmResponseParser} and {@code TableSelector} both depend on the
     * model staying close to that shape - though both also defend against it
     * not doing so.
     */
    public static String build(String query, List<TableMetadata> shortlist) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are selecting which database tables are relevant to a search query.\n\n");
        sb.append("Query: ").append(query).append("\n\n");
        sb.append("Candidate tables (name: description):\n");
        for (TableMetadata table : shortlist) {
            sb.append("- ").append(table.name());
            if (table.comment() != null && !table.comment().isBlank()) {
                sb.append(": ").append(table.comment());
            }
            sb.append("\n");
        }
        sb.append("\nReturn ONLY a JSON array of the table names that are relevant to the query, ");
        sb.append("drawn strictly from the candidate list above. ");
        sb.append("If none are relevant, return an empty array []. ");
        sb.append("Do not include any explanation.\n");
        sb.append("Example: [\"products\", \"orders\"]");
        return sb.toString();
    }
}
