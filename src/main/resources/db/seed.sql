-- ===========================================================================
-- Used Marketplace — demo data seeder
--
-- HOW TO RUN (one time), in this order:
--   1. Start the app once and let it boot fully, then stop it.
--      (Hibernate creates user_wallets + payments and the new img_url
--       columns through ddl-auto=update.)
--   2. psql -U postgres -d used -f src/main/resources/db/payment-indexes.sql
--   3. psql -U postgres -d used -f src/main/resources/db/seed.sql
--
-- All demo accounts use the password: password123 (real BCrypt hash below).
-- Images are stored in the project at static/images/ and referenced by
-- relative URL (they render as <img src="/images/....jpg">).
-- The seeder is safe to re-run: every INSERT skips rows that already exist.
-- ===========================================================================

BEGIN;

-- ---------- 1. User profiles (with avatar images) ----------

INSERT INTO user_profile (first_name, last_name, phone_number, profile_bio, img_url, created_at, updated_at)
SELECT 'Alice', 'Hassan', '+973 3300 1122',
       'Selling quality pre-owned electronics. Fast replies, honest descriptions.',
       '/images/avatar1.jpg', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM user_profile WHERE img_url = '/images/avatar1.jpg');

INSERT INTO user_profile (first_name, last_name, phone_number, profile_bio, img_url, created_at, updated_at)
SELECT 'Bob', 'Al-Mutairi', '+973 3300 3344',
       'Weekend seller. Gadgets, bikes and furniture from my garage.',
       '/images/avatar5.jpg', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM user_profile WHERE img_url = '/images/avatar5.jpg');

INSERT INTO user_profile (first_name, last_name, phone_number, profile_bio, img_url, created_at, updated_at)
SELECT 'Carol', 'Faris', '+973 3300 5566',
       'Bargain hunter. Ask me anything before you buy.',
       '/images/avatar12.jpg', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM user_profile WHERE img_url = '/images/avatar12.jpg');

-- ---------- 2. Users (password for all: password123) ----------

INSERT INTO users (username, email, password, role, is_banned, email_verified, user_profile_id, created_at, updated_at)
SELECT 'alice_seller', 'alice@used.local',
       '$2a$10$ksFrITsqFs8nZjQk8UK34.mq/HrwZqBQsnXhWzrm59sAgFYQL7da6',
       'USER', false, true, p.id, NOW(), NOW()
FROM user_profile p
WHERE p.img_url = '/images/avatar1.jpg'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'alice_seller');

INSERT INTO users (username, email, password, role, is_banned, email_verified, user_profile_id, created_at, updated_at)
SELECT 'bob_seller', 'bob@used.local',
       '$2a$10$ksFrITsqFs8nZjQk8UK34.mq/HrwZqBQsnXhWzrm59sAgFYQL7da6',
       'USER', false, true, p.id, NOW(), NOW()
FROM user_profile p
WHERE p.img_url = '/images/avatar5.jpg'
  AND NOT EXISTS (SELECT 1 FROM users WHERE username = 'bob_seller');

-- Buyers without profile pictures (user_profile_id stays NULL, which is allowed)
INSERT INTO users (username, email, password, role, is_banned, email_verified, user_profile_id, created_at, updated_at)
SELECT 'carol_buyer', 'carol@used.local',
       '$2a$10$ksFrITsqFs8nZjQk8UK34.mq/HrwZqBQsnXhWzrm59sAgFYQL7da6',
       'USER', false, true, NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'carol_buyer');

INSERT INTO users (username, email, password, role, is_banned, email_verified, user_profile_id, created_at, updated_at)
SELECT 'dan_buyer', 'dan@used.local',
       '$2a$10$ksFrITsqFs8nZjQk8UK34.mq/HrwZqBQsnXhWzrm59sAgFYQL7da6',
       'USER', false, true, NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'dan_buyer');

INSERT INTO users (username, email, password, role, is_banned, email_verified, user_profile_id, created_at, updated_at)
SELECT 'erin_buyer', 'erin@used.local',
       '$2a$10$ksFrITsqFs8nZjQk8UK34.mq/HrwZqBQsnXhWzrm59sAgFYQL7da6',
       'USER', false, true, NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'erin_buyer');

INSERT INTO users (username, email, password, role, is_banned, email_verified, user_profile_id, created_at, updated_at)
SELECT 'admin_user', 'admin.user@used.local',
       '$2a$10$ksFrITsqFs8nZjQk8UK34.mq/HrwZqBQsnXhWzrm59sAgFYQL7da6',
       'ADMIN', false, true, NULL, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin_user');

-- ---------- 3. Listings (with product images, ACTIVE) ----------

INSERT INTO listings (title, description, price, condition, location, img_url, status, user_id, created_at, updated_at)
SELECT 'MacBook Pro 13 inch 2020',
       'Apple M1, 16GB RAM, 512GB SSD. Battery health 92%. Comes with original charger.',
       850.00, 'Like new', 'Manama', '/images/laptop.jpg', 'ACTIVE', u.id, NOW(), NOW()
FROM users u WHERE u.username = 'alice_seller'
  AND NOT EXISTS (SELECT 1 FROM listings WHERE title = 'MacBook Pro 13 inch 2020');

INSERT INTO listings (title, description, price, condition, location, img_url, status, user_id, created_at, updated_at)
SELECT 'Sony WH-1000XM4 Headphones',
       'Noise cancelling, barely used, includes hard case and cable.',
       180.00, 'Like new', 'Manama', '/images/headphones.jpg', 'ACTIVE', u.id, NOW(), NOW()
FROM users u WHERE u.username = 'alice_seller'
  AND NOT EXISTS (SELECT 1 FROM listings WHERE title = 'Sony WH-1000XM4 Headphones');

INSERT INTO listings (title, description, price, condition, location, img_url, status, user_id, created_at, updated_at)
SELECT 'iPhone 13 128GB',
       'Battery 89%, no scratches, screen protector on since day one. Unlocked.',
       420.00, 'Good', 'Riffa', '/images/phone.jpg', 'ACTIVE', u.id, NOW(), NOW()
FROM users u WHERE u.username = 'bob_seller'
  AND NOT EXISTS (SELECT 1 FROM listings WHERE title = 'iPhone 13 128GB');

INSERT INTO listings (title, description, price, condition, location, img_url, status, user_id, created_at, updated_at)
SELECT 'Canon EOS M50 Camera',
       '24MP mirrorless with 15-45mm kit lens. Shutter count under 5k. Great for beginners.',
       380.00, 'Good', 'Muharraq', '/images/camera.jpg', 'ACTIVE', u.id, NOW(), NOW()
FROM users u WHERE u.username = 'bob_seller'
  AND NOT EXISTS (SELECT 1 FROM listings WHERE title = 'Canon EOS M50 Camera');

INSERT INTO listings (title, description, price, condition, location, img_url, status, user_id, created_at, updated_at)
SELECT 'City Bike FX2',
       'Aluminium frame size M, recently serviced, new tyres.',
       250.00, 'Good', 'Riffa', '/images/bike.jpg', 'ACTIVE', u.id, NOW(), NOW()
FROM users u WHERE u.username = 'bob_seller'
  AND NOT EXISTS (SELECT 1 FROM listings WHERE title = 'City Bike FX2');

INSERT INTO listings (title, description, price, condition, location, img_url, status, user_id, created_at, updated_at)
SELECT '3-Seater Sofa',
       'Grey fabric sofa, 2 years old, no stains or tears. Pickup only.',
       150.00, 'Fair', 'Hamala', '/images/sofa.jpg', 'ACTIVE', u.id, NOW(), NOW()
FROM users u WHERE u.username = 'bob_seller'
  AND NOT EXISTS (SELECT 1 FROM listings WHERE title = '3-Seater Sofa');

-- ---------- 4. Attach listings to the EXISTING categories ----------

INSERT INTO listing_categories (listing_id, category_id)
SELECT l.id, c.id FROM listings l JOIN categories c ON c.name = 'Computers'
WHERE l.title = 'MacBook Pro 13 inch 2020'
  AND NOT EXISTS (SELECT 1 FROM listing_categories lc WHERE lc.listing_id = l.id AND lc.category_id = c.id);

INSERT INTO listing_categories (listing_id, category_id)
SELECT l.id, c.id FROM listings l JOIN categories c ON c.name = 'Electronics'
WHERE l.title = 'MacBook Pro 13 inch 2020'
  AND NOT EXISTS (SELECT 1 FROM listing_categories lc WHERE lc.listing_id = l.id AND lc.category_id = c.id);

INSERT INTO listing_categories (listing_id, category_id)
SELECT l.id, c.id FROM listings l JOIN categories c ON c.name = 'Electronics'
WHERE l.title = 'Sony WH-1000XM4 Headphones'
  AND NOT EXISTS (SELECT 1 FROM listing_categories lc WHERE lc.listing_id = l.id AND lc.category_id = c.id);

INSERT INTO listing_categories (listing_id, category_id)
SELECT l.id, c.id FROM listings l JOIN categories c ON c.name = 'Phones'
WHERE l.title = 'iPhone 13 128GB'
  AND NOT EXISTS (SELECT 1 FROM listing_categories lc WHERE lc.listing_id = l.id AND lc.category_id = c.id);

INSERT INTO listing_categories (listing_id, category_id)
SELECT l.id, c.id FROM listings l JOIN categories c ON c.name = 'Electronics'
WHERE l.title = 'iPhone 13 128GB'
  AND NOT EXISTS (SELECT 1 FROM listing_categories lc WHERE lc.listing_id = l.id AND lc.category_id = c.id);

INSERT INTO listing_categories (listing_id, category_id)
SELECT l.id, c.id FROM listings l JOIN categories c ON c.name = 'Electronics'
WHERE l.title = 'Canon EOS M50 Camera'
  AND NOT EXISTS (SELECT 1 FROM listing_categories lc WHERE lc.listing_id = l.id AND lc.category_id = c.id);

INSERT INTO listing_categories (listing_id, category_id)
SELECT l.id, c.id FROM listings l JOIN categories c ON c.name = 'Sports'
WHERE l.title = 'City Bike FX2'
  AND NOT EXISTS (SELECT 1 FROM listing_categories lc WHERE lc.listing_id = l.id AND lc.category_id = c.id);

INSERT INTO listing_categories (listing_id, category_id)
SELECT l.id, c.id FROM listings l JOIN categories c ON c.name = 'Furniture'
WHERE l.title = '3-Seater Sofa'
  AND NOT EXISTS (SELECT 1 FROM listing_categories lc WHERE lc.listing_id = l.id AND lc.category_id = c.id);

-- ---------- 5. Wallets (starting balances for the payment process) ----------

INSERT INTO user_wallets (balance, user_id, created_at, updated_at)
SELECT 500.00, u.id, NOW(), NOW() FROM users u WHERE u.username = 'carol_buyer'
  AND NOT EXISTS (SELECT 1 FROM user_wallets w WHERE w.user_id = u.id);

INSERT INTO user_wallets (balance, user_id, created_at, updated_at)
SELECT 250.00, u.id, NOW(), NOW() FROM users u WHERE u.username = 'dan_buyer'
  AND NOT EXISTS (SELECT 1 FROM user_wallets w WHERE w.user_id = u.id);

-- Erin is broke on purpose: use her to demo the INSUFFICIENT_FUNDS race
INSERT INTO user_wallets (balance, user_id, created_at, updated_at)
SELECT 60.00, u.id, NOW(), NOW() FROM users u WHERE u.username = 'erin_buyer'
  AND NOT EXISTS (SELECT 1 FROM user_wallets w WHERE w.user_id = u.id);

INSERT INTO user_wallets (balance, user_id, created_at, updated_at)
SELECT 100.00, u.id, NOW(), NOW() FROM users u WHERE u.username = 'alice_seller'
  AND NOT EXISTS (SELECT 1 FROM user_wallets w WHERE w.user_id = u.id);

INSERT INTO user_wallets (balance, user_id, created_at, updated_at)
SELECT 30.00, u.id, NOW(), NOW() FROM users u WHERE u.username = 'bob_seller'
  AND NOT EXISTS (SELECT 1 FROM user_wallets w WHERE w.user_id = u.id);

COMMIT;

-- ---------- 6. Verification (what you should see below the result) ----------

SELECT u.username, u.role, w.balance
FROM users u LEFT JOIN user_wallets w ON w.user_id = u.id
WHERE u.username IN ('alice_seller','bob_seller','carol_buyer','dan_buyer','erin_buyer','admin_user')
ORDER BY u.username;

SELECT l.title, l.price, l.status, l.img_url, u.username AS seller
FROM listings l JOIN users u ON u.id = l.user_id
WHERE u.username IN ('alice_seller','bob_seller')
ORDER BY l.id DESC;
