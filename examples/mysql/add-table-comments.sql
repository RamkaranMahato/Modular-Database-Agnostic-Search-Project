-- =============================================================================
-- MCP Search - add table/column COMMENTS to improve schema-search table selection
-- =============================================================================
-- WHY THIS MATTERS
-- Whole-schema search picks tables by embedding each table's METADATA and
-- comparing it to your query. Without comments, that metadata is just:
--     "Table: products. Columns: id, title, body, category, price"
-- which tells the model almost nothing about what the table CONTAINS - so a
-- query like "cheap 4K display with USB-C" scores very low against it, and the
-- table may not be selected at all.
--
-- Adding comments turns that into something semantically rich:
--     "Table: products. Description: Catalogue of computer accessories,
--      peripherals and office equipment available for purchase... Columns: ..."
-- which matches real queries far better. This is the single highest-leverage
-- change for table-selection quality - much more so than tuning thresholds.
--
-- MySQL exposes these comments over JDBC (as REMARKS), which is exactly what
-- JdbcSchemaProvider reads.
--
-- Run with:  mysql -u root -p mydb < add-table-comments.sql
-- Then RESTART the app (table metadata is embedded once at startup).
-- =============================================================================

USE mydb;

-- --- products -------------------------------------------------------------
ALTER TABLE products
    COMMENT = 'Product catalogue of computer accessories, peripherals, displays, audio gear, storage, cables, power equipment and office furniture available for purchase. Each row is an item for sale with its name, marketing description, category and price. Use for questions about what products exist, their specifications, features, categories or prices.';

ALTER TABLE products
    MODIFY COLUMN title VARCHAR(255) NOT NULL
        COMMENT 'Product name as shown to customers, e.g. "Wireless Optical Mouse"',
    MODIFY COLUMN body TEXT NOT NULL
        COMMENT 'Marketing description of the product covering its features and specifications',
    MODIFY COLUMN category VARCHAR(100) NOT NULL
        COMMENT 'Product category such as accessories, audio, displays, storage, cables, bags, furniture, power',
    MODIFY COLUMN price DECIMAL(10,2) NOT NULL
        COMMENT 'Retail price of the product in dollars';

-- --- reviews (skip this block if you have not created the reviews table) ----
ALTER TABLE reviews
    COMMENT = 'Customer reviews and written feedback about products. Each row is one persons opinion after using an item, covering their real-world experience: comfort, durability, battery life, noise, build quality, value for money, and problems or complaints. Use for questions about opinions, satisfaction, complaints, what people say, or how products perform in practice.';

ALTER TABLE reviews
    MODIFY COLUMN headline VARCHAR(255) NOT NULL
        COMMENT 'Short summary of the reviewers overall verdict',
    MODIFY COLUMN comment TEXT NOT NULL
        COMMENT 'Full review text describing the customers experience, praise and complaints',
    MODIFY COLUMN reviewer VARCHAR(100) NOT NULL
        COMMENT 'Username of the person who wrote the review',
    MODIFY COLUMN rating TINYINT NOT NULL
        COMMENT 'Star rating from 1 (poor) to 5 (excellent)';

-- --- verify ---------------------------------------------------------------
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'mydb';

SELECT TABLE_NAME, COLUMN_NAME, COLUMN_COMMENT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = 'mydb' AND COLUMN_COMMENT <> ''
ORDER BY TABLE_NAME, ORDINAL_POSITION;

-- =============================================================================
-- AFTER RUNNING: restart the application. Table metadata is embedded ONCE at
-- startup, so the new comments only take effect on the next boot.
-- =============================================================================
