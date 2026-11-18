package io.mcpsearch.schema.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts a list of table names from an LLM's free-text response. LLMs
 * asked for "a JSON array of relevant table names" reliably return the array
 * but unreliably return <em>only</em> the array - they wrap it in prose,
 * markdown fences, or explanation. This parser digs the array out of that
 * mess without a JSON library (the schema module has no such dependency) and
 * without trusting the model's formatting.
 * <p>
 * Pure - fully unit-testable. It intentionally does <em>not</em> validate the
 * names against the real shortlist; that's {@code TableSelector}'s job, which
 * already intersects the result against the shortlist to drop hallucinations.
 * This class only turns text into a candidate list of strings.
 */
public final class LlmResponseParser {

    private LlmResponseParser() {
    }

    /**
     * Pull table names out of a response. Handles: a bare JSON array
     * {@code ["a","b"]}, an array embedded in prose or ```-fences, single or
     * double quotes, and extra whitespace. Returns names in first-seen order,
     * de-duplicated. Returns an empty list if no array can be found - the
     * caller treats empty as "fall back to the shortlist".
     */
    public static List<String> parseTableNames(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        int start = response.indexOf('[');
        int end = response.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            return List.of();
        }
        String inside = response.substring(start + 1, end);

        List<String> names = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawToken : inside.split(",")) {
            String name = stripQuotesAndWhitespace(rawToken);
            if (!name.isEmpty() && seen.add(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static String stripQuotesAndWhitespace(String token) {
        String t = token.trim();
        // strip one layer of matching single or double quotes
        if (t.length() >= 2) {
            char first = t.charAt(0);
            char last = t.charAt(t.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                t = t.substring(1, t.length() - 1).trim();
            }
        }
        return t;
    }
}
