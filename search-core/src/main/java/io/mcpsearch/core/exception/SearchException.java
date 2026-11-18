package io.mcpsearch.core.exception;

/**
 * Base checked exception for everything that can go wrong executing a
 * search or indexing operation. Checked deliberately: adapters talk to
 * external systems (databases, embedding APIs) that fail in ways callers
 * need to consciously handle rather than let propagate as unchecked runtime
 * noise.
 */
public class SearchException extends Exception {

    private static final long serialVersionUID = 1L;

    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
