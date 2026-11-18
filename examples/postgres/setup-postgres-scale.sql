-- =============================================================================
-- MCP Search - PostgreSQL schema for LARGE tables (tested shape for ~10M rows)
-- =============================================================================
-- This is the schema the Postgres adapter needs in order to scale. The two
-- indexes below are what separate "millisecond index lookup" from "sequential
-- scan of every row", and neither is created automatically.
--
-- Run with:  psql -U postgres -d mydb -f setup-postgres-scale.sql
-- =============================================================================

CREATE EXTENSION IF NOT EXISTS vector;

-- --- table ----------------------------------------------------------------
DROP TABLE IF EXISTS products;

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    title       TEXT NOT NULL,
    body        TEXT NOT NULL,
    category    TEXT NOT NULL,
    price       NUMERIC(10,2) NOT NULL,

    -- Semantic channel. Dimensions MUST match your embedding model
    -- (1536 = text-embedding-3-small).
    embedding   VECTOR(1536),

    -- Text channel. A STORED generated column: Postgres computes the tsvector
    -- once on write and keeps it current automatically on update.
    --
    -- This is the single most important line in the file for large tables.
    -- Computing to_tsvector(...) inside the query instead means tokenising every
    -- row on every search - no index can help, and cost grows linearly with the
    -- table. A stored column can carry a GIN index, so lookups stay fast at any
    -- size. Point the adapter at it with tsvectorColumn = 'search_vector'.
    --
    -- Note the 'english' literal: the language must be a constant for the
    -- expression to be immutable (and therefore indexable).
    search_vector tsvector GENERATED ALWAYS AS (
        to_tsvector('english', coalesce(title, '') || ' ' || coalesce(body, ''))
    ) STORED
);

-- --- text index -----------------------------------------------------------
-- GIN is the right choice for search: slower to build and update than GiST,
-- but much faster to query, which is the trade you want for a read-heavy
-- search workload.
CREATE INDEX products_search_vector_idx ON products USING gin (search_vector);

-- --- vector index ---------------------------------------------------------
-- HNSW turns O(n) brute-force similarity into an approximate graph walk. This
-- is what makes 10M rows viable at all.
--
-- IMPORTANT, build it AFTER bulk-loading and embedding your rows. Building the
-- index first makes every insert pay index-maintenance cost; building it after
-- is dramatically faster overall.
--
-- vector_cosine_ops must match the distance operator the adapter uses (<=>).
-- Using the wrong opclass means the index silently never gets used.
--
--   m               = graph connections per node. Higher = better recall,
--                     bigger index, slower build. 16 is the usual default.
--   ef_construction = candidate list size while building. Higher = better
--                     index quality, slower build. 64 is a reasonable start.
--
-- On 10M x 1536 this build takes hours and wants a lot of memory. Raise
-- maintenance_work_mem first or it will spill to disk and crawl:
--
--   SET maintenance_work_mem = '8GB';
--   SET max_parallel_maintenance_workers = 7;
--
CREATE INDEX products_embedding_idx ON products
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- --- filter columns -------------------------------------------------------
-- Plain btree indexes for the columns used in structured filters. Without
-- these, a filtered search still scans.
CREATE INDEX products_category_idx ON products (category);
CREATE INDEX products_price_idx    ON products (price);

-- =============================================================================
-- QUERY-TIME SETTINGS (the adapter can apply these for you - see config)
-- =============================================================================
--   SET hnsw.ef_search = 100;
--     How far the search walks the graph. The default (40) favours speed over
--     recall; raise it until recall is acceptable, measure with the eval harness.
--
--   SET hnsw.iterative_scan = relaxed_order;
--     Needed when you combine vector search WITH filters. HNSW walks
--     nearest-first and filters afterwards, so a selective WHERE can return far
--     fewer rows than your LIMIT. Iterative scan keeps pulling until satisfied.
--     (pgvector 0.8+.)
--
-- Set these via adapter config: hnswEfSearch / hnswIterativeScan.
-- =============================================================================

-- =============================================================================
-- ADAPTER CONFIGURATION TO MATCH THIS SCHEMA
-- =============================================================================
--   search:
--     datasource:
--       type: postgres
--     postgres:
--       table: products
--       id-column: id
--       text-search-expression: title      # only used when tsvector-column is absent
--       tsvector-column: search_vector     # <- the scaling one
--       vector-column: embedding
--       language: english
--       exact-total-hits: false            # skip COUNT(*) OVER() full scan
--       hnsw-ef-search: 100
--       hnsw-iterative-scan: true
-- =============================================================================

-- =============================================================================
-- VERIFY THE INDEXES ARE ACTUALLY USED (do this - do not assume)
-- =============================================================================
-- EXPLAIN ANALYZE
--   SELECT id, title FROM products
--   WHERE search_vector @@ plainto_tsquery('english', 'wireless mouse')
--   ORDER BY ts_rank(search_vector, plainto_tsquery('english','wireless mouse')) DESC
--   LIMIT 10;
--   -> want "Bitmap Index Scan on products_search_vector_idx", NOT "Seq Scan"
--
-- EXPLAIN ANALYZE
--   SELECT id, title FROM products
--   ORDER BY embedding <=> '[...]'::vector LIMIT 10;
--   -> want "Index Scan using products_embedding_idx", NOT "Seq Scan"
--
-- A Seq Scan on either means the index is missing, the opclass is wrong, or
-- the query shape does not match the index expression.
-- =============================================================================
