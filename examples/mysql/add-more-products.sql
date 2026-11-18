-- =============================================================================
-- MCP Search - additional sample records
-- =============================================================================
-- Adds more rows to the existing `products` table (from setup-mysql.sql).
-- embedding is left NULL on all new rows - run the backfill afterward to
-- populate them (set app.backfill.enabled=true, run once, set it back).
--
-- The picks here deliberately spread across categories and include items whose
-- RELEVANCE to a query like "wireless mouse" or "something to carry my laptop"
-- is semantic, not keyword - so hybrid search has something interesting to do.
--
-- Run with:
--   mysql -u root -p mydb < add-more-products.sql
-- or paste into your SQL client (make sure you're USE-ing mydb).
-- =============================================================================

USE mydb;

INSERT INTO products (title, body, category, price) VALUES
    ('Ergonomic Vertical Mouse',
     'Handshake-position wireless mouse designed to reduce wrist strain during long work sessions.',
     'accessories', 34.99),

    ('Trackball Mouse',
     'Stationary pointing device controlled with the thumb; ideal for tight desk spaces.',
     'accessories', 42.00),

    ('Laptop Backpack 15-inch',
     'Padded backpack with a dedicated laptop compartment, water-resistant shell, and USB pass-through.',
     'bags', 59.99),

    ('Messenger Laptop Bag',
     'Slim shoulder bag that fits a 14-inch laptop, tablet, and documents; padded strap.',
     'bags', 44.50),

    ('Mechanical Keyboard TKL',
     'Tenkeyless mechanical keyboard with linear red switches and a compact footprint for gamers.',
     'accessories', 74.99),

    ('Wireless Keyboard Slim',
     'Low-profile Bluetooth keyboard with a long battery life and quiet scissor-switch keys.',
     'accessories', 49.99),

    ('4K Ultra-Wide Monitor 34-inch',
     'Curved ultra-wide display with a 144Hz refresh rate, perfect for immersive work and play.',
     'displays', 599.00),

    ('Portable Monitor 15.6-inch',
     'Lightweight USB-C travel monitor that gives your laptop a second screen anywhere.',
     'displays', 179.00),

    ('USB-C Docking Station',
     'Single-cable dock adding HDMI, Ethernet, SD card reader, and multiple USB ports to a laptop.',
     'cables', 129.99),

    ('Thunderbolt 4 Cable',
     'Certified 0.8-meter Thunderbolt cable supporting 40Gbps data and 100W charging.',
     'cables', 34.00),

    ('Wireless Earbuds Pro',
     'True-wireless earbuds with adaptive noise cancellation, spatial audio, and a compact charging case.',
     'audio', 149.00),

    ('Studio Over-Ear Headphones',
     'Wired reference headphones with a flat frequency response for mixing and mastering.',
     'audio', 249.00),

    ('Desk Microphone USB',
     'Cardioid USB condenser microphone with a built-in pop filter for streaming and calls.',
     'audio', 89.00),

    ('External HDD 4TB',
     'Desktop external hard drive offering huge capacity for backups and media libraries.',
     'storage', 99.99),

    ('NVMe SSD Enclosure',
     'USB 3.2 Gen 2 enclosure that turns an M.2 NVMe drive into a blazing-fast portable disk.',
     'storage', 29.99),

    ('Laptop Cooling Pad',
     'Five-fan cooling stand with adjustable height that keeps a gaming laptop running cool.',
     'accessories', 32.50),

    ('Adjustable Standing Desk',
     'Electric sit-stand desk with programmable height presets and a spacious work surface.',
     'furniture', 379.00),

    ('Ergonomic Office Chair',
     'Mesh-back office chair with lumbar support, adjustable armrests, and a reclining tilt.',
     'furniture', 219.00),

    ('Webcam 4K with Auto-Framing',
     'Ultra HD webcam that automatically keeps you centered, with HDR and dual microphones.',
     'accessories', 129.00),

    ('Wireless Charging Pad',
     'Fast Qi charging pad for phones and earbuds with a non-slip surface and status LED.',
     'accessories', 24.00);

-- --- checks ---------------------------------------------------------------
SELECT COUNT(*) AS total_rows FROM products;
SELECT COUNT(*) AS rows_missing_embedding FROM products WHERE embedding IS NULL;
