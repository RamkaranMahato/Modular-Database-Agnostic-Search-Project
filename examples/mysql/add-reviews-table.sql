-- =============================================================================
-- MCP Search - second table (`reviews`) for testing WHOLE-SCHEMA search
-- =============================================================================
-- Adds a `reviews` table that:
--   * is SEARCHABLE (has text columns + a FULLTEXT index + a VECTOR column), so
--     the schema-search table-selection stage will consider it a candidate;
--   * has a FOREIGN KEY to `products`, so the FK-expansion stage has a real
--     relationship to traverse.
--
-- Without a second related table, `search_schema` is a degenerate demo - it can
-- only ever pick `products` and has nothing to expand to. This gives it a real
-- decision to make: a query about opinions/complaints should lean toward
-- `reviews`, a query about specs/prices toward `products`.
--
-- Run with:  mysql -u root -p mydb < add-reviews-table.sql
-- or paste into your SQL client with `mydb` selected.
--
-- AFTER RUNNING: re-run the backfill (app.backfill.enabled=true) pointed at this
-- table to populate its embeddings - see the note at the bottom.
-- =============================================================================

USE mydb;

DROP TABLE IF EXISTS reviews;

CREATE TABLE reviews (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    product_id  BIGINT       NOT NULL,
    reviewer    VARCHAR(100) NOT NULL,
    headline    VARCHAR(255) NOT NULL,
    comment     TEXT         NOT NULL,
    rating      TINYINT      NOT NULL,
    embedding   VECTOR(1536) NULL,          -- MySQL 9.0+; same dims as products
    PRIMARY KEY (id),
    CONSTRAINT fk_reviews_product
        FOREIGN KEY (product_id) REFERENCES products (id)
        ON DELETE CASCADE,
    FULLTEXT INDEX ft_reviews (headline, comment)
) ENGINE=InnoDB;

-- --- sample reviews -------------------------------------------------------
-- Deliberately written in "customer opinion" language (durability, comfort,
-- battery life, value, noise) rather than spec language, so a semantic query
-- about EXPERIENCES leans toward this table while a query about SPECS leans
-- toward products. product_id 1-10 refer to the original seed products.
INSERT INTO reviews (product_id, reviewer, headline, comment, rating) VALUES
    (1, 'anita_k',   'Comfortable for long sessions',
     'My wrist used to ache after a full workday. Weeks in, that discomfort is basically gone. Tracking is smooth on any surface.', 5),
    (1, 'devon_r',   'Battery lasts forever',
     'Still on the original set of batteries after four months of daily use. The receiver has never dropped connection.', 4),
    (2, 'priya_s',   'Loud but satisfying',
     'The keys are clicky enough that my family notices from another room. Typing feel is excellent, though the noise is real.', 4),
    (2, 'marcus_t',  'Backlighting is uneven',
     'A couple of keys glow dimmer than the rest. Build quality is otherwise solid and the switches feel great.', 3),
    (3, 'lena_m',    'Colours look fantastic',
     'Setup took minutes and the picture is sharp from edge to edge. The stand wobbles slightly if the desk is bumped.', 5),
    (4, 'sam_o',     'Cable frayed after months',
     'Charging speed was great initially, but the braiding near the connector started coming apart with regular use.', 2),
    (5, 'tara_v',    'Blocks out the office completely',
     'Noise cancellation is genuinely impressive on a commute. Ear cups get a bit warm during long listening sessions.', 5),
    (5, 'ibrahim_a', 'Comfort fades after hours',
     'Sound quality is rich and detailed, but the clamping pressure gets uncomfortable past the two hour mark.', 3),
    (6, 'nina_p',    'Sturdy and well made',
     'No flex at all under a heavy laptop, and it runs noticeably cooler now that air can circulate underneath.', 5),
    (7, 'oscar_d',   'Grainy in low light',
     'Perfectly usable in a bright room, but picture quality falls apart in the evening without a lamp on.', 3),
    (8, 'yuki_h',    'Blazing fast transfers',
     'Moving large video files takes a fraction of the time it used to. Runs warm but never uncomfortably hot.', 5),
    (9, 'greg_l',    'Surprisingly full sound',
     'For something this small the bass response is impressive. Pairing occasionally needs a second attempt.', 4),
    (10, 'farah_j',  'Great value for the size',
     'Covers the whole desk and the stitched edge shows no fraying after months. Slides slightly on a glass surface.', 4),
    (3, 'ellis_w',   'Worth the upgrade',
     'Coming from a smaller screen this changed how I work. Having two documents side by side is a huge productivity win.', 5),
    (1, 'kaito_n',   'Stopped working after a drop',
     'Worked flawlessly until it fell off the desk once. Not especially rugged, so treat it carefully.', 2);

-- --- checks ---------------------------------------------------------------
SELECT COUNT(*) AS review_rows FROM reviews;
SELECT COUNT(*) AS reviews_missing_embedding FROM reviews WHERE embedding IS NULL;

-- confirm the FK is visible to the schema reader (this is what FK expansion walks):
SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = 'mydb' AND REFERENCED_TABLE_NAME IS NOT NULL;

-- =============================================================================
-- EMBEDDING THESE ROWS
-- =============================================================================
-- The backfill runner targets ONE table per run. To embed reviews, temporarily
-- point it at this table in application.yml:
--
--   app:
--     backfill:
--       enabled: true
--       table: reviews
--       id-column: id
--       vector-column: embedding
--       text-columns:
--         - headline
--         - comment
--
-- Run the app once (it backfills and exits), then set enabled back to false
-- and restore table: products if you want the products backfill config back.
-- Schema search needs BOTH tables embedded to compare them properly.
-- =============================================================================
