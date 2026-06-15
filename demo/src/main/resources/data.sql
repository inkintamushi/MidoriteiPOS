INSERT INTO staff_users (staff_id, password_hash, display_name, role, active)
VALUES ('staff', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', 'スタッフ', 'STAFF', TRUE)
ON DUPLICATE KEY UPDATE
  password_hash = VALUES(password_hash),
  display_name = VALUES(display_name),
  role = VALUES(role),
  active = VALUES(active);

INSERT INTO product_categories (code, name, display_order, active) VALUES
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

INSERT INTO products (id, name, category, price, image_path, sold_out, active) VALUES
(1, 'つくね', 'yaki', 180, '/images/product3.jpg', FALSE, TRUE),
(2, '日本酒', 'alcohol', 550, '/images/product2.jpg', FALSE, TRUE),
(3, '水', 'softdrink', 320, '/images/product5.jpg', FALSE, TRUE),
(4, 'いなり', 'yaki', 450, '/images/product7.jpg', FALSE, TRUE),
(5, 'ビール', 'alcohol', 810, '/images/product8.jpg', FALSE, TRUE),
(6, '飲み放題プラン', 'nomi', 1980, '/images/product6.jpg', FALSE, TRUE),
(7, 'くにくにの鶏皮', 'yaki', 160, '/images/product4.jpg', FALSE, TRUE),
(8, 'ねぎま', 'yaki', 220, '/images/product1.jpg', FALSE, TRUE),
(9, 'もも', 'yaki', 200, '/images/product3.jpg', FALSE, TRUE),
(10, '砂肝', 'yaki', 190, '/images/product4.jpg', FALSE, TRUE),
(11, 'ハイボール', 'alcohol', 500, '/images/product2.jpg', FALSE, TRUE),
(12, 'レモンサワー', 'alcohol', 480, '/images/product8.jpg', FALSE, TRUE),
(13, 'ウーロン茶', 'softdrink', 300, '/images/product5.jpg', FALSE, TRUE),
(14, 'コーラ', 'softdrink', 300, '/images/product5.jpg', FALSE, TRUE),
(15, 'バニラアイス', 'dessert', 380, '/images/product6.jpg', FALSE, TRUE),
(16, '季節のシャーベット', 'dessert', 420, '/images/product6.jpg', FALSE, TRUE),
(17, 'きゅうり浅漬け', 'tsukemono', 350, '/images/product7.jpg', FALSE, TRUE),
(18, '枝豆', 'ippin', 330, '/images/product1.jpg', FALSE, TRUE),
(19, '唐揚げ', 'ippin', 680, '/images/product4.jpg', FALSE, TRUE),
(20, '焼き鳥盛り合わせ', 'moriawase', 980, '/images/product3.jpg', FALSE, TRUE),
(21, '食べ放題プラン', 'tabehoudai', 2980, '/images/product6.jpg', FALSE, TRUE)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  category = VALUES(category),
  price = VALUES(price),
  image_path = VALUES(image_path),
  sold_out = VALUES(sold_out),
  active = VALUES(active);

INSERT INTO dining_tables (table_number, guest_count, status) VALUES
(1, 0, 'EMPTY'),
(2, 0, 'EMPTY'),
(3, 0, 'EMPTY'),
(4, 0, 'EMPTY'),
(5, 0, 'STOPPED'),
(6, 0, 'EMPTY'),
(7, 0, 'EMPTY'),
(8, 0, 'STOPPED'),
(9, 0, 'EMPTY')
ON DUPLICATE KEY UPDATE
  table_number = VALUES(table_number);
