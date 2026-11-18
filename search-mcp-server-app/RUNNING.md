# Running the MCP Search server (MySQL)

A concrete, start-to-finish guide to boot `search-mcp-server-app` against a
real MySQL database. This is the point where the whole project becomes a
running thing you can connect an MCP client to.

## What you need first

- **MySQL 9.0 or newer** for vector/hybrid search (8.x = text-only; check with
  `SELECT VERSION();`).
- **JDK 21+** and **Maven**.
- An **OpenAI API key** (or swap in another Spring AI model provider — see the
  bottom). This is what computes embeddings; the database never does.

## 1. Prepare the database

Create/choose a table and add the two search structures. Example for a
`products` table:

```sql
-- full-text index over the columns you want keyword search on:
ALTER TABLE products ADD FULLTEXT INDEX ft_products (title, body);

-- vector column for semantic search (MySQL 9.0+), sized to your embedding model.
-- text-embedding-3-small = 1536 dimensions:
ALTER TABLE products ADD COLUMN embedding VECTOR(1536);
```

The `embedding` column starts empty. It gets populated either by your own
ingestion job or by this server when you index rows through it — see
"Populating embeddings" below. Search won't return semantic matches for rows
whose `embedding` is still NULL.

## 2. Configure

Edit `search-mcp-server-app/src/main/resources/application.yml` (or override
via env/CLI). At minimum, set the connection and the table/columns to match
your schema. Then export the secrets:

```bash
export DB_PASSWORD='your-mysql-password'
export OPENAI_API_KEY='sk-...'
```

Key settings that must line up:
- `app.search.embedding-dimensions` **must equal** your `VECTOR(n)` size.
- `spring.ai.openai.embedding.options.model` must be the model matching that
  dimension count.
- `search.mysql.text-columns` must be exactly the columns in your `FULLTEXT`
  index.

## 3. Build and run

```bash
# from the repo root
mvn -pl search-mcp-server-app -am package
java -jar search-mcp-server-app/target/search-mcp-server-app-0.1.0-SNAPSHOT.jar
```

Or for development:

```bash
mvn -pl search-mcp-server-app -am spring-boot:run
```

On startup the server auto-configures the MySQL adapter and exposes an MCP
`search` tool over Streamable-HTTP. The adapter's health check will fail fast
if `vector-column` is set but the server isn't actually 9.0+, so a version
mismatch surfaces immediately rather than at first query.

## 4. Connect an MCP client

Point any MCP client (Claude Desktop, etc.) at the server's Streamable-HTTP
endpoint. It will discover the `search` tool and can call it with a query
string, optional `topK`, and optional exact-match `filters`.

## Populating embeddings

Two ways to get vectors into the `embedding` column:

1. **Let the server do it** — because an `EmbeddingProvider` bean is wired in,
   indexing a row through the adapter without a precomputed vector will embed
   the text automatically. (Row-at-a-time; convenient.)
2. **Precompute in bulk** — run your existing rows through the same embedding
   model offline and `UPDATE` the column. Faster for backfilling a large
   table. Whatever you use, it must be the **same model** the server queries
   with, or distances are meaningless.

## Optional: whole-schema search

To expose the second tool that searches across many tables and follows
foreign keys, set:

```yaml
app:
  search:
    schema-search-enabled: true
    llm-refine-enabled: true    # optional; uses the chat model to refine table selection
```

On startup this reads your schema via JDBC metadata, embeds each searchable
table's metadata once, and registers a `search_schema` MCP tool. Its results
are tagged with the origin table since they span multiple tables.

## Using PostgreSQL instead

Swap the driver in `search-mcp-server-app/pom.xml` (comment MySQL, uncomment
Postgres), set `search.datasource.type: postgres` with a `search.postgres.*`
block (see the starter docs), and use pgvector's `vector` column type. The
adapter and the rest of the pipeline are otherwise identical.

## A caveat worth repeating

The application/wiring layer (this app + the starter) targets Spring Boot 4.1
/ Spring AI 2.0 and was written against current documentation but **not
compiled in the environment it was authored in** — no access to Spring
artifacts there. The cross-module calls into this project's own verified code
were signature-checked, but the Spring-facing glue (bean wiring, the `@McpTool`
annotations, the Spring AI model calls) should be treated as needing that
first real `mvn package` to shake out. If the `@McpTool` import doesn't
resolve, try `org.springframework.ai.mcp.server.annotation` instead of
`org.springframework.ai.mcp.annotation`.
