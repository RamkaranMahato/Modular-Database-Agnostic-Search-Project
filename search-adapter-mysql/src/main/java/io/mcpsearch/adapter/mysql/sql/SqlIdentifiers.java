package io.mcpsearch.adapter.mysql.sql;

import java.util.regex.Pattern;

/**
 * Validates and backtick-quotes MySQL identifiers. A deliberate near-copy of
 * the Postgres adapter's equivalent (which double-quotes instead) - the two
 * adapters stay independent rather than sharing a util, so neither drags the
 * other onto the classpath. Identifier validation matters most for
 * {@link io.mcpsearch.core.model.Filter#field()}, which can arrive from an
 * untrusted MCP tool call and is interpolated into SQL (JDBC can't
 * parameterize identifiers).
 */
public final class SqlIdentifiers {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    private SqlIdentifiers() {
    }

    public static void validate(String identifier, String paramName) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    paramName + " must be a valid SQL identifier (letters, digits, underscore; "
                            + "not starting with a digit): " + identifier);
        }
    }

    /** Backtick-quotes an already-{@link #validate validated} identifier. Backticks in the
     *  input are impossible because {@link #validate} forbids them, so no escaping is needed. */
    public static String quote(String identifier) {
        return "`" + identifier + "`";
    }
}
