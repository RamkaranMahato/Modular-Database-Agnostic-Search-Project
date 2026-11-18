package io.mcpsearch.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Standalone MCP search server. Run with {@code java -jar} after building, or
 * {@code mvn -pl search-mcp-server-app spring-boot:run} during development.
 * <p>
 * Almost everything is auto-configured: the starter picks the adapter from
 * {@code search.datasource.type} and exposes the MCP {@code search} tool, and
 * Spring AI's OpenAI starter provides the embedding + chat models. The beans
 * this app adds itself (in {@link SearchServerConfig}) are just the small
 * adapters that connect Spring AI's model types to this project's SPI
 * interfaces, plus the optional whole-schema tool.
 */
@SpringBootApplication
public class SearchServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServerApplication.class, args);
    }
}
