-- =============================================================================
-- MCP Search - MySQL test database setup
-- =============================================================================
-- Creates a database and a `products` table wired for HYBRID search:
--   - a FULLTEXT index for the keyword/text channel
--   - a native VECTOR column for the semantic channel (MySQL 9.0+ ONLY)
-- and seeds it with sample rows you can search immediately (text search works
-- right away; semantic search works once embeddings are populated - see the
-- note at the very bottom).
--
-- Run it with:
--   mysql -u root -p < setup-mysql.sql
-- or paste it into MySQL Workbench / any client and execute.
--
-- REQUIRES MySQL 9.0+ for the VECTOR column. On 8.x, delete the two lines
-- flagged "9.0+ ONLY" below and you'll still get a working text-search demo.
-- =============================================================================

-- Check your version first (should print 9.x.x for vector support):
SELECT VERSION() AS mysql_version;

-- --- database -------------------------------------------------------------
CREATE DATABASE IF NOT EXISTS mydb
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;   -- ai_ci = accent/case-insensitive, good for search

USE mydb;

-- Start clean if re-running:
DROP TABLE IF EXISTS products;

-- --- table ----------------------------------------------------------------
CREATE TABLE products (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(255) NOT NULL,
    body        TEXT         NOT NULL,
    category    VARCHAR(100) NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    embedding   VECTOR(1536) NULL,          -- 9.0+ ONLY; 1536 = text-embedding-3-small
    PRIMARY KEY (id),
    FULLTEXT INDEX ft_products (title, body) -- powers MATCH ... AGAINST
) ENGINE=InnoDB;

-- --- sample data ----------------------------------------------------------
-- embedding is left NULL on purpose - it gets filled in by the embedding model
-- later (see bottom). Text search works against these rows immediately.
INSERT INTO products (title, body, category, price) VALUES
    ('Wireless Optical Mouse',
     'Ergonomic cordless mouse with adjustable DPI and a silent scroll wheel. USB receiver included.',
     'accessories', 24.99),

    ('Mechanical Keyboard RGB',
     'Hot-swappable mechanical keyboard with tactile brown switches and per-key RGB backlighting.',
     'accessories', 89.99),

    ('27-inch 4K Monitor',
     'Ultra HD IPS display with HDR support, USB-C power delivery, and a height-adjustable stand.',
     'displays', 349.00),

    ('USB-C Charging Cable',
     'Braided 2-meter USB-C to USB-C cable supporting 100W fast charging and data transfer.',
     'cables', 12.50),

    ('Noise-Cancelling Headphones',
     'Over-ear wireless headphones with active noise cancellation and 30-hour battery life.',
     'audio', 199.00),

    ('Laptop Stand Aluminium',
     'Adjustable aluminium laptop stand that improves airflow and raises the screen to eye level.',
     'accessories', 39.95),

    ('1080p Webcam',
     'Full HD webcam with autofocus, dual noise-reducing microphones, and a privacy shutter.',
     'accessories', 54.00),

    ('Portable SSD 1TB',
     'Pocket-sized external solid-state drive with USB 3.2 and up to 1050 MB/s read speeds.',
     'storage', 119.99),

    ('Bluetooth Desk Speaker',
     'Compact wireless speaker with rich bass, 12-hour playback, and a built-in microphone.',
     'audio', 45.00),

    ('Gaming Mouse Pad XL',
     'Extra-large cloth mouse pad with a stitched edge and a non-slip rubber base.',
     'accessories', 18.99);

-- --- quick checks ---------------------------------------------------------
-- how many rows:
SELECT COUNT(*) AS total_rows FROM products;

-- try the TEXT channel right now (this works without any embeddings):
SELECT id, title,
       MATCH(title, body) AGAINST('wireless mouse' IN NATURAL LANGUAGE MODE) AS relevance
FROM products
WHERE MATCH(title, body) AGAINST('wireless mouse' IN NATURAL LANGUAGE MODE)
ORDER BY relevance DESC;

-- how many rows still need embeddings (all of them, initially):
SELECT COUNT(*) AS rows_missing_embedding FROM products WHERE embedding IS NULL;

-- =============================================================================
-- POPULATING EMBEDDINGS (required for the semantic / hybrid channel)
-- =============================================================================
-- The `embedding` column is NULL for every row above. The database does NOT
-- fill it - an embedding model does. Two ways to populate it:
--
--   1. THROUGH THE SERVER (easiest to start): once the MCP server is running
--      with an EmbeddingProvider configured, index each row through it (e.g.
--      via the adapter's index() path). The server embeds `title`/`body` and
--      writes the vector back. Text search works before this; semantic/hybrid
--      results appear per-row as embeddings get filled in.
--
--   2. BACKFILL YOURSELF: run each row's text through the SAME embedding model
--      your server uses (text-embedding-3-small here), then update the row.
--      The vector literal is a JSON array cast with STRING_TO_VECTOR, e.g.:
--
--        UPDATE products
--        SET embedding = STRING_TO_VECTOR('[0.0123, -0.0456, ...1536 values...]')
--        WHERE id = 1;
--
-- Whichever you pick, the model MUST match the one the server queries with,
-- and its dimension count MUST equal VECTOR(1536) above. Change both together
-- if you use a different model.
-- =============================================================================
