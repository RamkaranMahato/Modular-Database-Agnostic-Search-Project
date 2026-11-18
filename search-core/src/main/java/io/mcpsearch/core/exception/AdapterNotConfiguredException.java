package io.mcpsearch.core.exception;

/**
 * Thrown by the auto-configuration layer (in the starter module) when
 * {@code search.datasource.type} names a datastore whose adapter isn't on
 * the classpath, or when the property is missing entirely. Kept here in
 * search-core, not the starter, so adapter modules can also throw it
 * consistently during their own setup validation.
 */
public class AdapterNotConfiguredException extends SearchException {

    private static final long serialVersionUID = 1L;

    public AdapterNotConfiguredException(String message) {
        super(message);
    }

    public AdapterNotConfiguredException(String message, Throwable cause) {
        super(message, cause);
    }
}
