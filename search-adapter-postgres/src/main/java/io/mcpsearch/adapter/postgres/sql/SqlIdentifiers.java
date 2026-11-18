package io.mcpsearch.adapter.postgres.sql;

import java.util.regex.Pattern;

/**
 * Validates and quotes SQL identifiers (table/column names). JDBC's
 * {@code PreparedStatement} can parameterize values but never identifiers,
 * so table/column names inserted into generated SQL must be validated
 * defensively - this matters most for {@link io.mcpsearch.core.model.Filter#field()},
 * which can originate from an MCP tool call and should be treated as
 * untrusted input.
 */
public final class SqlIdentifiers {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private SqlIdentifiers() {
    }

    /**
     * @throws IllegalArgumentException if {@code identifier} is null, blank, or contains
     *                                  anything other than ASCII letters/digits/underscore
     *                                  (and doesn't start with a digit)
     */
    public static void validate(String identifier, String paramName) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    paramName + " must be a valid SQL identifier (letters, digits, underscore; "
                            + "not starting with a digit): " + identifier);
        }
    }

    /** Wraps an already-{@link #validate validated} identifier in double quotes for safe interpolation. */
    public static String quote(String identifier) {
        return "\"" + identifier + "\"";
    }
}
