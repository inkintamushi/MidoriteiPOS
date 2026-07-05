-- ============================================================================
-- Legacy schema cleanup.
--
-- spring.sql.init.mode=always re-runs this file on every startup. The tables
-- below are being replaced by a restructured schema (see docs/詳細設計/DB詳細設計.xlsx)
-- even where a name is reused (e.g. products, order_items), so CREATE TABLE IF
-- NOT EXISTS alone would leave any already-existing table in its old shape.
-- Dropping first guarantees every environment converges on the new structure.
--
-- NOTE: keeping these DROPs here means every future restart wipes and
-- reseeds all data (acceptable for this pre-launch prototype stage). Once
-- every environment (local + deployed MySQL) has been restarted at least
-- once against this file, consider removing the DROP TABLE block below so
-- restarts stop discarding in-progress orders/table sessions.
-- ============================================================================
SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS table_qr_codes;
DROP TABLE IF EXISTS sales_records;
DROP TABLE IF EXISTS order_items;
DROP TABLE IF EXISTS customer_orders;
DROP TABLE IF EXISTS products;
DROP TABLE IF EXISTS dining_tables;
DROP TABLE IF EXISTS product_categories;
DROP TABLE IF EXISTS staff_users;
SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 1. stores (店舗)
-- ============================================================================
CREATE TABLE IF NOT EXISTS stores (
  id   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 2. categories (カテゴリ) — was product_categories
-- ============================================================================
CREATE TABLE IF NOT EXISTS categories (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  code          VARCHAR(50)  NOT NULL UNIQUE,
  name          VARCHAR(100) NOT NULL UNIQUE,
  display_order INTEGER NOT NULL DEFAULT 0,
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT chk_categories_name CHECK (name <> '')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 3. courses (コース) — new concept, not yet wired into any UI
-- ============================================================================
CREATE TABLE IF NOT EXISTS courses (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  name        VARCHAR(100) NOT NULL,
  price       DECIMAL(10,0) NOT NULL,
  duration    VARCHAR(100) NOT NULL,
  -- 1 = ノーマル, 2 = プレミアム (numeric mapping assumed; not specified in
  -- DB詳細設計.xlsx, which only lists the bare labels)
  course_type TINYINT UNSIGNED NOT NULL,
  CONSTRAINT chk_courses_price CHECK (price >= 0),
  CONSTRAINT chk_courses_type  CHECK (course_type IN (1, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 4. employees (従業員) — was staff_users
-- ============================================================================
CREATE TABLE IF NOT EXISTS employees (
  id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  staff_id      VARCHAR(50)  NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name  VARCHAR(100) NOT NULL,
  role          VARCHAR(30)  NOT NULL DEFAULT 'STAFF',
  active        BOOLEAN NOT NULL DEFAULT TRUE,
  store_id      BIGINT UNSIGNED NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_employees_store FOREIGN KEY (store_id) REFERENCES stores(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 5. dining_tables (卓)
-- ============================================================================
CREATE TABLE IF NOT EXISTS dining_tables (
  id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  store_id     BIGINT UNSIGNED NOT NULL,
  table_number INT UNSIGNED NOT NULL,
  -- 1 cleaning-unhandled(清掃未対応), 2 call-unhandled(呼出未対応),
  -- 3 cleaning-needs-help(清掃要応援), 4 call-needs-help(呼出要応援),
  -- 5 cleaning-in-progress(清掃対応中), 6 call-in-progress(呼出対応中),
  -- 7 available(使用可能), 8 out-of-service(使用中止), 9 occupied(使用中)
  seat_status  TINYINT UNSIGNED NOT NULL DEFAULT 7,
  CONSTRAINT fk_dining_tables_store FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT uq_dining_tables_store_number UNIQUE (store_id, table_number),
  CONSTRAINT chk_dining_tables_seat_status CHECK (seat_status BETWEEN 1 AND 9)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 6. products (商品)
-- ============================================================================
CREATE TABLE IF NOT EXISTS products (
  id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  store_id    BIGINT UNSIGNED NOT NULL,
  category_id BIGINT UNSIGNED NOT NULL,
  name        VARCHAR(100) NOT NULL,
  price       DECIMAL(10,0) NOT NULL,
  image_path  VARCHAR(255) NOT NULL DEFAULT '/images/product1.jpg',
  sold_out    BOOLEAN NOT NULL DEFAULT FALSE,
  active      BOOLEAN NOT NULL DEFAULT TRUE,
  created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_products_store      FOREIGN KEY (store_id) REFERENCES stores(id),
  CONSTRAINT fk_products_category   FOREIGN KEY (category_id) REFERENCES categories(id),
  CONSTRAINT uq_products_store_name UNIQUE (store_id, name),
  CONSTRAINT chk_products_price CHECK (price >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 7. customer_groups (利用客グループ)
-- ============================================================================
CREATE TABLE IF NOT EXISTS customer_groups (
  id             BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  entered_at     DATETIME NOT NULL,
  left_at        DATETIME NULL,
  course_id      BIGINT UNSIGNED NULL,
  -- 1 receiving/in-progress(受付中), 2 paid(会計済み),
  -- 4 uncollected(未集金), 8 billing-in-progress(会計中)
  billing_status TINYINT UNSIGNED NOT NULL DEFAULT 1,
  guest_count    INT UNSIGNED NOT NULL,
  CONSTRAINT fk_customer_groups_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT chk_customer_groups_left_at CHECK (left_at IS NULL OR left_at >= entered_at),
  CONSTRAINT chk_customer_groups_billing_status CHECK (billing_status IN (1, 2, 4, 8)),
  CONSTRAINT chk_customer_groups_guest_count CHECK (guest_count >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 8. table_sessions (客卓履歴) — a customer_group's occupancy of one table;
-- also carries the QR/secret code, folding in what table_qr_codes used to do.
-- ============================================================================
CREATE TABLE IF NOT EXISTS table_sessions (
  id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  customer_group_id BIGINT UNSIGNED NOT NULL,
  table_id          BIGINT UNSIGNED NOT NULL,
  started_at        DATETIME NOT NULL,
  ended_at          DATETIME NULL,
  qr_code           VARCHAR(255) NOT NULL UNIQUE,
  secret_code       CHAR(6) NOT NULL,
  CONSTRAINT fk_table_sessions_group FOREIGN KEY (customer_group_id) REFERENCES customer_groups(id),
  CONSTRAINT fk_table_sessions_table FOREIGN KEY (table_id) REFERENCES dining_tables(id),
  CONSTRAINT chk_table_sessions_ended_at CHECK (ended_at IS NULL OR ended_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 9. course_products (コース対象商品) — junction table, new concept
-- ============================================================================
CREATE TABLE IF NOT EXISTS course_products (
  course_id  BIGINT UNSIGNED NOT NULL,
  product_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (course_id, product_id),
  CONSTRAINT fk_course_products_course  FOREIGN KEY (course_id)  REFERENCES courses(id),
  CONSTRAINT fk_course_products_product FOREIGN KEY (product_id) REFERENCES products(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 10. orders (注文) — was customer_orders; now references a table_session
-- instead of a table_number directly.
-- ============================================================================
CREATE TABLE IF NOT EXISTS orders (
  id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  table_session_id BIGINT UNSIGNED NOT NULL,
  ordered_at       DATETIME NOT NULL,
  CONSTRAINT fk_orders_table_session FOREIGN KEY (table_session_id) REFERENCES table_sessions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- 11. order_items (注文明細)
-- ============================================================================
CREATE TABLE IF NOT EXISTS order_items (
  id                 BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
  order_id           BIGINT UNSIGNED NOT NULL,
  product_id         BIGINT UNSIGNED NOT NULL,
  product_name       VARCHAR(100) NOT NULL,
  quantity           INT UNSIGNED NOT NULL,
  delivered_quantity INT UNSIGNED NOT NULL DEFAULT 0,
  canceled_quantity  INT UNSIGNED NOT NULL DEFAULT 0,
  unit_price         DECIMAL(10,0) NOT NULL,
  status             VARCHAR(30) NOT NULL DEFAULT 'ORDERED',
  completed_at       DATETIME NULL,
  CONSTRAINT fk_order_items_order   FOREIGN KEY (order_id) REFERENCES orders(id),
  CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT chk_order_items_quantity   CHECK (quantity >= 1),
  CONSTRAINT chk_order_items_delivered  CHECK (delivered_quantity >= 0),
  CONSTRAINT chk_order_items_canceled   CHECK (canceled_quantity >= 0),
  CONSTRAINT chk_order_items_qty_bounds CHECK (delivered_quantity + canceled_quantity <= quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
