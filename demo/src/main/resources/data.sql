INSERT INTO stores (id, name) VALUES
(1, 'みどり亭')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO employees (staff_id, password_hash, display_name, role, active, store_id)
VALUES ('staff', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', 'スタッフ', 'STAFF', TRUE, 1)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  display_name = VALUES(display_name),
  role = VALUES(role),
  active = VALUES(active),
  store_id = VALUES(store_id);

INSERT INTO categories (code, name, display_order, active) VALUES
('yaki', '焼き鳥', 10, TRUE),
('alcohol', 'アルコール', 20, TRUE),
('softdrink', 'ソフトドリンク', 30, TRUE),
('dessert', 'デザート', 40, TRUE),
('tsukemono', '漬物', 50, TRUE),
('ippin', '一品', 60, TRUE),
('moriawase', '盛り合わせ', 70, TRUE),
('tabehoudai', '食べ放題', 80, TRUE),
('nomi', '飲み放題', 90, TRUE)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  display_order = VALUES(display_order),
  active = VALUES(active);

-- category_id is resolved via a scalar subquery on the stable `code`, so this
-- doesn't depend on categories having been inserted in any particular order
-- or on their auto-generated ids.
INSERT INTO products (id, store_id, category_id, name, price, image_path, sold_out, active) VALUES
(1, 1, (SELECT id FROM categories WHERE code = 'yaki'), 'つくね', 180, '/images/product3.jpg', FALSE, TRUE),
(2, 1, (SELECT id FROM categories WHERE code = 'alcohol'), '日本酒', 550, '/images/product2.jpg', FALSE, TRUE),
(3, 1, (SELECT id FROM categories WHERE code = 'softdrink'), '水', 320, '/images/product5.jpg', FALSE, TRUE),
(4, 1, (SELECT id FROM categories WHERE code = 'yaki'), 'いなり', 450, '/images/product7.jpg', FALSE, TRUE),
(5, 1, (SELECT id FROM categories WHERE code = 'alcohol'), 'ビール', 810, '/images/product8.jpg', FALSE, TRUE),
(6, 1, (SELECT id FROM categories WHERE code = 'nomi'), '飲み放題プラン', 1980, '/images/product6.jpg', FALSE, TRUE),
(7, 1, (SELECT id FROM categories WHERE code = 'yaki'), 'くにくにの鶏皮', 160, '/images/product4.jpg', FALSE, TRUE),
(8, 1, (SELECT id FROM categories WHERE code = 'yaki'), 'ねぎま', 220, '/images/product1.jpg', FALSE, TRUE),
(9, 1, (SELECT id FROM categories WHERE code = 'yaki'), 'もも', 200, '/images/product3.jpg', FALSE, TRUE),
(10, 1, (SELECT id FROM categories WHERE code = 'yaki'), '砂肝', 190, '/images/product4.jpg', FALSE, TRUE),
(11, 1, (SELECT id FROM categories WHERE code = 'alcohol'), 'ハイボール', 500, '/images/product2.jpg', FALSE, TRUE),
(12, 1, (SELECT id FROM categories WHERE code = 'alcohol'), 'レモンサワー', 480, '/images/product8.jpg', FALSE, TRUE),
(13, 1, (SELECT id FROM categories WHERE code = 'softdrink'), 'ウーロン茶', 300, '/images/product5.jpg', FALSE, TRUE),
(14, 1, (SELECT id FROM categories WHERE code = 'softdrink'), 'コーラ', 300, '/images/product5.jpg', FALSE, TRUE),
(15, 1, (SELECT id FROM categories WHERE code = 'dessert'), 'バニラアイス', 380, '/images/product6.jpg', FALSE, TRUE),
(16, 1, (SELECT id FROM categories WHERE code = 'dessert'), '季節のシャーベット', 420, '/images/product6.jpg', FALSE, TRUE),
(17, 1, (SELECT id FROM categories WHERE code = 'tsukemono'), 'きゅうり浅漬け', 350, '/images/product7.jpg', FALSE, TRUE),
(18, 1, (SELECT id FROM categories WHERE code = 'ippin'), '枝豆', 330, '/images/product1.jpg', FALSE, TRUE),
(19, 1, (SELECT id FROM categories WHERE code = 'ippin'), '唐揚げ', 680, '/images/product4.jpg', FALSE, TRUE),
(20, 1, (SELECT id FROM categories WHERE code = 'moriawase'), '焼き鳥盛り合わせ', 980, '/images/product3.jpg', FALSE, TRUE),
(21, 1, (SELECT id FROM categories WHERE code = 'tabehoudai'), '食べ放題プラン', 2980, '/images/product6.jpg', FALSE, TRUE)
ON DUPLICATE KEY UPDATE
  category_id = VALUES(category_id),
  name = VALUES(name),
  price = VALUES(price),
  image_path = VALUES(image_path),
  sold_out = VALUES(sold_out),
  active = VALUES(active);

INSERT INTO courses (id, name, price, duration, course_type) VALUES
(1, 'ノーマル', 1980, '90分', 1),
(2, 'プレミアム', 2980, '120分', 2)
ON DUPLICATE KEY UPDATE
  price = VALUES(price),
  duration = VALUES(duration);

-- seat_status: old EMPTY -> 7 (available), old STOPPED -> 8 (out-of-service)
INSERT INTO dining_tables (store_id, table_number, seat_status) VALUES
(1, 1, 7),
(1, 2, 7),
(1, 3, 7),
(1, 4, 7),
(1, 5, 8),
(1, 6, 7),
(1, 7, 7),
(1, 8, 8),
(1, 9, 7)
ON DUPLICATE KEY UPDATE seat_status = VALUES(seat_status);
