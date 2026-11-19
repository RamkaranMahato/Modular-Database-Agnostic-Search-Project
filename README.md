# MCP Search — `search-core`

Step 1 of the modular hybrid search MCP project: the datastore-agnostic
domain model and SPI. Everything else (Postgres/Mongo/Elasticsearch/pgvector
adapters, the Spring Boot starter, the MCP server wrapper) depends on this
module; this module depends on nothing but the JDK.

## Why dependency-free

Keeping `search-core` free of Spring, JSON, and driver dependencies isn't
just tidiness — it's what makes the plug-and-play story actually true.
Adapter modules and the starter can each pull in exactly what they need
without this module dragging in a competing version of something. It also
means `search-core` compiles and unit-tests in isolation, in any Java build,
which is a good sign the abstraction is honest rather than secretly coupled
to Spring idioms.



## What's here

```
io.mcpsearch.core.model
  SearchQuery      – request: text and/or vector, filters, mode, pagination, sort (builder)
  SearchResult      – one hit: id, fused score, text/vector score breakdown, source fields
  SearchResponse   – page of results + totalHits + timing
  Document          – what you index: id, fields, optional precomputed vector
  Filter            – structured filter clause (EQ, RANGE, IN, CONTAINS, ...)
  SortField         – explicit sort override
  FusionConfig      – alpha / rrfK / topK, with sane defaults
  SearchMode, FusionMode, FilterOperator, HealthStatus – enums/small value types

io.mcpsearch.core.spi
  SearchAdapter      – the contract every datastore integration implements
  EmbeddingProvider  – pluggable text -> vector, independent of which datastore you use
  FusionStrategy     – combines a text result list and a vector result list

io.mcpsearch.core.fusion
  ReciprocalRankFusion  – default. Rank-based, no score normalization needed.
  WeightedSumFusion     – alpha-tunable alternative, min-max normalizes each channel first.
  FusionStrategyFactory – FusionMode -> FusionStrategy

io.mcpsearch.core.exception
  SearchException, AdapterNotConfiguredException, EmbeddingGenerationException
```

## Key design decisions baked into this module

- **`SearchAdapter.search()` returns one `SearchResponse` regardless of mode.**
  An adapter with native hybrid support (Elasticsearch) can fuse internally;
  an adapter without it runs both channels and calls a `FusionStrategy`
  itself. Callers never know the difference.
- **RRF is the default fusion mode** because it needs no tuning and doesn't
  care that BM25 and cosine-similarity scores live on different scales.
  `WEIGHTED` is there for teams that want direct, interpretable control via
  `alpha` and are willing to tune it.
- **`EmbeddingProvider` is separate from `SearchAdapter`** — which datastore
  you search and which model you embed with are independent choices.
- **`Document.vector()` and `SearchQuery.vector()` are both optional.**
  Callers with precomputed embeddings can pass them directly; callers
  without can leave them null and let the adapter call an
  `EmbeddingProvider` internally. `search-core` doesn't force either path.
- **Records for everything immutable, a hand-written builder only for
  `SearchQuery`** (too many optional fields for a record to stay readable).
  All array-typed accessors (`Document.vector()`, `SearchQuery.vector()`)
  defensively clone on both the way in and the way out.

## Building

This module needs only a JDK — no external dependencies for `main`. Test
sources use JUnit 5, resolved normally via Maven Central:

```
mvn -pl search-core -am test
```

(This sandbox couldn't reach Maven Central to run `mvn` itself, so the
fusion and validation logic was instead verified directly against the
compiled classes with a standalone assertion harness — all checks passed,
including: a doc appearing in both channels outranking a single-channel
doc under RRF, `alpha=1.0`/`alpha=0.0` weighted fusion exactly reproducing
single-channel ranking, equal-score channels not dividing by zero, and
`SearchQuery`/`Filter` validation rejecting malformed input. The JUnit
tests under `src/test` cover the same cases in the form that ships with
the repo.)

## `search-adapter-postgres`

The first `SearchAdapter` implementation. Text channel via `tsvector`/
`ts_rank`; vector channel via the pgvector extension's `<=>` cosine
operator. Postgres has no single-query hybrid operator, so `HYBRID` mode
runs both channels as separate queries and fuses them in-app using
`search-core`'s `FusionStrategy` — this is the "adapter without native
hybrid support" path described in `SearchAdapter`'s javadoc.

Compile-time dependency is `javax.sql.DataSource` / `java.sql.*` only — no
PostgreSQL driver jar needed to build this module. Your application supplies
a configured `DataSource` (HikariCP, Spring's, etc.) and the driver jar at
runtime.

```java
var config = PostgresAdapterConfig.builder("products", "id", "title")
        .vectorColumn("embedding")   // omit if this table has no vector column
        .build();

SearchAdapter adapter = new PostgresSearchAdapter(dataSource, config);

var response = adapter.search(SearchQuery.builder()
        .text("wireless mouse")
        .filter(Filter.range("price", 10, 50))
        .size(10)
        .build());
```

Internals are split so the SQL-generation logic (`PostgresQueryBuilder`,
`FilterSqlRenderer`, `SqlIdentifiers`, `PgVectorCodec`) is pure and
unit-testable without a live database, and `PostgresSearchAdapter` itself is
a thin JDBC execution wrapper around it. `SqlIdentifiers` validates every
table/column name against a strict identifier pattern before it's
interpolated into SQL — this matters most for `Filter.field()`, which can
originate from an untrusted MCP tool call.

**Known v1 limitations, called out rather than hidden:**
- `totalHits` in `HYBRID` mode is an approximation (`max` of each channel's
  own exact count) — see the javadoc on `PostgresSearchAdapter.search()`.
- `to_tsvector` is recomputed per row per query; for large tables, add a
  stored/generated `tsvector` column and point `textSearchExpression` at it.
- Only cosine distance (`<=>`) is supported for the vector channel; L2/inner
  product can be added as a config option later if needed.
- This sandbox has no path to a live Postgres instance or Maven Central, so
  this module is verified by (a) strict `javac` compilation and (b) a
  standalone assertion harness checking the exact generated SQL and
  parameter order for every query type, including SQL-injection rejection
  via malicious filter field names. It has not been run against a real
  database — do that before shipping it.

## `search-mcp-server-starter`

Spring Boot auto-configuration that turns `search-adapter-postgres` into an
actual MCP server tool. This is where the plug-and-play config from the
spec becomes real: add this starter to a Spring Boot app, fill in one
config block, and an MCP `search` tool exists.

**Targets Spring Boot 4.1 / Spring AI 2.0 (GA June 12, 2026)**, using Spring
AI's annotation-based MCP programming model (`@McpTool`/`@McpToolParam`) -
the current idiomatic way to expose a tool, replacing the older manual
`ToolCallback` registration approach.

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/mydb
    username: myuser
    password: ${DB_PASSWORD}
  ai:
    mcp:
      server:
        name: my-search-server
        version: 1.0.0
        protocol: STREAMABLE

search:
  datasource:
    type: postgres
  postgres:
    table: products
    id-column: id
    text-search-expression: title
    vector-column: embedding      # omit if this table has no vector column
  hybrid:
    default-mode: RRF
    default-alpha: 0.5
    default-top-k: 10
```

That's the whole "plug and play" story: `spring.datasource.*` gets you a
`DataSource` via Spring Boot's own auto-configuration (bring your own driver
jar), `search.datasource.type: postgres` selects the adapter, `search.postgres.*`
tells it which table/columns to use, and `SearchAutoConfiguration` does the
rest - constructing the adapter and exposing it as an MCP tool with zero
additional code.

**How the pieces fit:**
- `SearchProperties` binds the `search.*` namespace (via constructor-bound
  records, current Spring Boot convention)
- `SearchAutoConfiguration` builds a `PostgresSearchAdapter` bean from those
  properties plus a `DataSource` (which it does *not* configure itself -
  that's standard Spring Boot `DataSourceAutoConfiguration`, triggered by
  your own `spring.datasource.*`)
- `SearchToolService` is the actual MCP surface: one `@McpTool`-annotated
  `search` method, with a deliberately simplified parameter set (query text,
  topK, flat exact-match filters) rather than the full `Filter` operator
  set - that's what an LLM tool-caller can use reliably

**Verification status - please read before using this module.** Unlike
`search-core` and `search-adapter-postgres`, **this module could not be
compiled in this sandbox at all** - there's no path here to Maven Central or
any Spring/Spring AI artifact, only to a handful of allow-listed domains
(GitHub, npm, PyPI, etc.), none of which serve these jars. Everything here
was written against current Spring AI 2.0 documentation (fetched live, not
from training data, since this API changed significantly and recently), not
compiled or run. Two specific things to double check when you build this for
real:
1. The import for `@McpTool`/`@McpToolParam` in `SearchToolService.java` -
   documentation sources disagree on whether it's
   `org.springframework.ai.mcp.annotation` or
   `org.springframework.ai.mcp.server.annotation` post-GA. I went with the
   former based on the most recent (post-GA) sources, but flagged it
   in-code; if it doesn't resolve, try the other.
2. Exact current patch versions for `spring-boot.version` (`4.1.0`) and
   `spring-ai.version` (`2.0.0`) in the POM - bump to whatever's latest when
   you build.

## `search-schema`

Whole-schema search: given a query over a large (50+ table) schema, find the
relevant tables and search only those, rather than searching everything or
making the caller name a table. "Relevant" means two things at once here —
tables related *to the query* (semantic selection) and tables related *to
those* via foreign keys (schema-graph traversal).

The pipeline, end to end:
1. **Embed the query** (via `search-core`'s `EmbeddingProvider`).
2. **Select query-relevant tables** — hybrid shortlist-then-refine: an
   embedding cosine ranking narrows 50+ tables to a shortlist
   (`TableEmbeddingIndex`), then an optional LLM `TableRefiner` prunes it to
   the genuinely relevant subset (`TableSelector`). Table embeddings are
   computed once at startup over each table's name/columns/comment; only the
   query is embedded per search.
3. **Expand along foreign keys** — from the selected "seed" tables, walk the
   FK graph outward by a configurable direction and depth, with a hard cap so
   a hub table like `users` can't drag in the whole schema (`SchemaGraph`).
4. **Search each selected table** with the existing hybrid `SearchAdapter`,
   one call per table (`AdapterFactory` bridges table metadata → adapter).
5. **Fuse across tables** with RRF, each hit tagged with its origin table
   since rows from different tables have entirely different shapes
   (`CrossTableFusion`, `TableScopedResult`).

**Design choices worth calling out:**
- **Auto-discovery with config override**: which tables are *searchable* is
  discovered from schema metadata (a table needs a text or vector column to
  qualify) but overridable — that discovery is behind the `SchemaProvider`
  SPI, so the rest of the module tests against hand-built schemas.
- **RRF everywhere scores aren't comparable**: within-table (text vs vector)
  and now across-table too. Different tables' relevance scores are even less
  comparable than text-vs-vector, and RRF's rank-only formula sidesteps that.
- **Defensive around the LLM**: the refiner's output is intersected back
  against the shortlist (LLMs hallucinate table names), and any refiner
  failure or empty result falls back to the shortlist rather than failing the
  search. Enforced in `TableSelector`, not trusted to the refiner.
- **Per-table failure isolation**: in the orchestrator, one table's search
  failing (missing vector column, transient error) is swallowed and the query
  continues — a partial result beats a failed one.

**What's the pure, verified core vs. the I/O edges.** The judgement-heavy
logic — FK graph traversal (direction, depth, cap, seed-preservation), cosine
ranking with threshold, the selector's hallucination-dropping and fallbacks,
cross-table fusion, and the orchestrator's per-table failure isolation — is
pure, depends only on `search-core` + the JDK, and **was compiled and
assertion-tested in this sandbox** (all passing). The I/O edges are behind
three SPI interfaces that were **not** run here because they need a live
environment: `SchemaProvider` (reads `information_schema`), `TableRefiner`
(calls an LLM), and `AdapterFactory` (builds per-table adapters over a real
`DataSource`). Those three concrete implementations still need writing and
testing against a real database and model — they're deliberately small and
clearly marked so that work is contained.

## `search-adapter-mysql`

The second `SearchAdapter`, for **MySQL 9.0+**. Text channel via
`MATCH ... AGAINST` over a `FULLTEXT` index; vector channel via MySQL's native
`VECTOR` type and `DISTANCE()` function; hybrid fused in-app via `search-core`,
same as the Postgres adapter.

**Version requirement is real, not incidental:** the `VECTOR` type and
`STRING_TO_VECTOR`/`DISTANCE` functions only exist in MySQL 9.0+. On 8.x
there's no native vector type at all, so vector/hybrid search isn't possible
without a plugin or external store — text-only search still works. The
adapter's `healthCheck()` actively probes for `STRING_TO_VECTOR` when a vector
column is configured, so a mis-versioned server fails fast at startup rather
than at first query.

Where MySQL genuinely differs from Postgres, and why the adapter had to be
separate rather than a config flag:
- text search is `MATCH(cols) AGAINST(? IN NATURAL LANGUAGE MODE)` and the
  matched columns **must be covered by a declared `FULLTEXT` index** — it
  can't search an arbitrary expression the way Postgres's `to_tsvector` can.
  So the config takes `text-columns` (plural), not a single expression.
- upsert is `INSERT ... AS new ON DUPLICATE KEY UPDATE col = new.col` (the
  non-deprecated 8.0.19+ row-alias form), not `ON CONFLICT`.
- identifiers are backtick-quoted, not double-quoted.
- the distance metric (`COSINE`/`DOT`/`EUCLIDEAN`) is validated against an
  allowlist since it's interpolated into SQL, not parameterizable.

```java
var config = MysqlAdapterConfig.builder("products", "id", List.of("title", "body"))
        .vectorColumn("embedding")     // omit for text-only; requires MySQL 9.0+
        .distanceMetric("COSINE")
        .build();

SearchAdapter adapter = new MysqlSearchAdapter(dataSource, config);
```

Verified the same way as the Postgres adapter — strict `javac` plus a
standalone assertion harness over the exact generated SQL (text, vector,
upsert alias form, delete, filters, injection rejection via malicious filter
fields and distance metric). Not run against a live MySQL; do that before
shipping.

**A ready-to-use config file lives at `examples/mysql/application.yml`**
(and `.properties`), with the DataSource, adapter config, the one-time
`FULLTEXT`-index and `VECTOR`-column DDL, and a plain-language explainer of how
embeddings get populated. That's the file to start from for a MySQL setup.

### `search-schema` concrete implementations (`io.mcpsearch.schema.impl`)

The three SPIs now have concrete implementations, each structured so its
judgement-bearing logic is pure and tested while the genuine I/O stays thin:

- **`JdbcSchemaProvider`** reads the schema through JDBC `DatabaseMetaData`
  (backed by `information_schema` on both Postgres and MySQL) — tables,
  columns, primary keys, comments, and foreign keys. The column-type
  classification it depends on is pulled out into **`ColumnClassifier`**
  (pure, covers both DB type vocabularies, strips `(n)` suffixes, tested).
  The JDBC walking itself is the I/O edge — needs a live DB or a
  Testcontainers integration test.
- **`LlmTableRefiner`** implements the optional refine stage against a plain
  `UnaryOperator<String>` (prompt → completion), so it's tied to no AI SDK —
  wire it to Spring AI's `ChatClient`, OpenAI, Bedrock, or a fake. The two
  hard parts are pure and tested: **`RefinerPromptBuilder`** (prompt
  construction) and **`LlmResponseParser`**, which digs the table-name array
  out of messy LLM output (markdown fences, prose, single quotes, dupes)
  without a JSON dependency. The refiner also maps names back through the
  shortlist, so a hallucinated name is dropped here as well as in
  `TableSelector`.
- **`CachingAdapterFactory`** wraps a caller-supplied
  `Function<TableMetadata, SearchAdapter>` with per-table caching. The
  function indirection is deliberate: `search-schema` must not depend on the
  Postgres or MySQL adapter modules, so the wiring layer supplies the closure
  that builds the right adapter. Caching behaviour is tested with a counting
  fake.
- **`SchemaSearchFactory`** is the startup assembler: load schema → filter to
  searchable tables (optionally intersected with a config allow-list) → embed
  each table's metadata once → build the `TableEmbeddingIndex`,
  `TableSelector`, and `SchemaSearchOrchestrator`. Call it once at startup.

Verified in-sandbox: `ColumnClassifier`, `LlmResponseParser`,
`LlmTableRefiner`'s map-back, and `CachingAdapterFactory` caching, all via
assertion harness (now mirrored as JUnit tests). Still needing a real
environment: the JDBC reads in `JdbcSchemaProvider`, a real chat transport
behind `LlmTableRefiner`, and a real embedding model behind
`SchemaSearchFactory`'s startup embedding.

## `search-mcp-server-app` — the runnable server

The standalone Spring Boot application you actually run. Depends on the
starter (which auto-configures the adapter and the single-table `search`
tool), adds the concrete embedding provider and the optional whole-schema
`search_schema` tool, and packages as an executable jar.

- **`SearchServerApplication`** — the `@SpringBootApplication` entry point.
- **`SpringAiEmbeddingProvider`** — bridges Spring AI's `EmbeddingModel` to
  this project's `EmbeddingProvider` SPI. This is the concrete thing that
  calls the embedding model; swapping OpenAI for Bedrock/local is just which
  `EmbeddingModel` bean Spring wires in.
- **`SearchServerConfig`** — the small amount of hand-wiring: the embedding
  bean, and (when `app.search.schema-search-enabled=true`) the whole-schema
  orchestrator assembled from `JdbcSchemaProvider`, an optional
  `LlmTableRefiner` over Spring AI's `ChatClient`, and a `CachingAdapterFactory`
  that builds per-table MySQL adapters from discovered metadata.
- **`SchemaSearchToolService`** — exposes `search_schema` as a second MCP tool,
  conditional on the orchestrator bean existing.
- **`application.yml`** — MySQL-configured, with the schema-search toggle.
- **`RUNNING.md`** — a concrete start-to-finish boot guide.

**Verification note:** the cross-module calls this app makes into the
project's own (verified) modules were signature-checked with `javap` against
the compiled classes and all match. The Spring-facing glue — bean wiring,
`@McpTool` annotations, Spring AI model calls — targets Spring Boot 4.1 /
Spring AI 2.0 and is written from current docs but was **not** compilable in
the authoring sandbox; the first real `mvn package` is the compile check for
that layer.

## Next steps

1. First real `mvn package` on a machine with Maven Central access — shakes
   out any Spring/Spring AI API drift in the app + starter layer
2. Remaining adapters (Mongo, Elasticsearch), if you want more datastores
