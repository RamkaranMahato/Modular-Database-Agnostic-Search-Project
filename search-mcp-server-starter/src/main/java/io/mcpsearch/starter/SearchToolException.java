package io.mcpsearch.starter;

/**
 * Thrown by {@link SearchToolService} when the underlying {@code SearchAdapter}
 * call fails. Unchecked deliberately: the {@code @McpTool}-annotated method
 * needs to signal failure back to Spring AI's tool invocation machinery,
 * which reports it to the calling MCP client as a tool error - the correct
 * outcome for a failed search, rather than silently returning empty results.
 */
public class SearchToolException extends RuntimeException {

    public SearchToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
