MERGE INTO products (id, name, category, price, image_path, sold_out) KEY(id)
VALUES (1, 'つくね', 'yaki', 180, '/images/product3.jpg', FALSE);

MERGE INTO products (id, name, category, price, image_path, sold_out) KEY(id)
VALUES (2, '日本酒', 'alcohol', 550, '/images/product2.jpg', FALSE);

MERGE INTO products (id, name, category, price, image_path, sold_out) KEY(id)
VALUES (3, '水', 'alcohol', 320, '/images/product5.jpg', FALSE);

MERGE INTO products (id, name, category, price, image_path, sold_out) KEY(id)
VALUES (4, 'いなり', 'yaki', 450, '/images/product7.jpg', FALSE);

MERGE INTO products (id, name, category, price, image_path, sold_out) KEY(id)
VALUES (5, 'ビール', 'alcohol', 810, '/images/product8.jpg', FALSE);

MERGE INTO products (id, name, category, price, image_path, sold_out) KEY(id)
VALUES (6, '飲み放題プラン', 'nomi', 1980, '/images/product6.jpg', FALSE);

MERGE INTO products (id, name, category, price, image_path, sold_out) KEY(id)
VALUES (7, 'くにくにの鶏皮', 'yaki', 160, '/images/product4.jpg', FALSE);

ALTER TABLE products ALTER COLUMN id RESTART WITH 8;
