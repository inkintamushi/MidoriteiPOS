package com.example.demo;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class ProductApiController {

	private static final long DEFAULT_STORE_ID = 1L;

	private final JdbcTemplate jdbcTemplate;

	public ProductApiController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/api/categories")
	public List<Map<String, Object>> categories() {
		return jdbcTemplate.queryForList("""
				SELECT code, name, display_order, active
				FROM categories
				WHERE active = TRUE AND code <> 'tabehoudai'
				ORDER BY display_order, id
				""");
	}

	@GetMapping("/api/products")
	public List<Map<String, Object>> products() {
		return jdbcTemplate.queryForList("""
				SELECT p.id, p.name, c.code AS category, c.name AS category_name, p.price, p.image_path, p.sold_out, p.active
				FROM products p
				JOIN categories c ON c.id = p.category_id
				WHERE p.active = TRUE AND c.active = TRUE AND c.code <> 'tabehoudai' AND p.store_id = ?
				ORDER BY c.display_order, p.id
				""", DEFAULT_STORE_ID);
	}

	@PostMapping("/api/admin/products")
	public Map<String, Object> addProduct(@RequestBody ProductRequest request) {
		String name = normalizeName(request.name());
		int price = requirePositivePrice(request.price());
		long categoryId = resolveCategoryId(request.category());
		String imagePath = defaultImage(request.imagePath());
		Long activeProductId = firstLong(
				"SELECT id FROM products WHERE store_id = ? AND name = ? AND active = TRUE",
				DEFAULT_STORE_ID, name);
		if (activeProductId != null) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "商品名が重複しています。");
		}
		Long deletedProductId = firstLong(
				"SELECT id FROM products WHERE store_id = ? AND name = ? AND active = FALSE",
				DEFAULT_STORE_ID, name);
		if (deletedProductId != null) {
			jdbcTemplate.update("""
					UPDATE products
					SET category_id = ?,
					    price = ?,
					    image_path = ?,
					    sold_out = FALSE,
					    active = TRUE
					WHERE id = ? AND store_id = ?
					""", categoryId, price, imagePath, deletedProductId, DEFAULT_STORE_ID);
			jdbcTemplate.update("DELETE FROM course_products WHERE product_id = ?", deletedProductId);
			return Map.of("ok", true, "id", deletedProductId, "reactivated", true);
		}

		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO products (store_id, category_id, name, price, image_path, sold_out, active)
					VALUES (?, ?, ?, ?, ?, FALSE, TRUE)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, DEFAULT_STORE_ID);
			ps.setLong(2, categoryId);
			ps.setString(3, name);
			ps.setInt(4, price);
			ps.setString(5, imagePath);
			return ps;
		}, keyHolder);
		return Map.of("ok", true, "id", generatedProductId(keyHolder));
	}

	@PutMapping("/api/admin/products/{id}")
	public Map<String, Object> updateProduct(@PathVariable long id, @RequestBody ProductRequest request) {
		Long categoryId = request.category() == null ? null : resolveCategoryId(request.category());
		jdbcTemplate.update("""
				UPDATE products
				SET name = COALESCE(?, name),
				    category_id = COALESCE(?, category_id),
				    price = COALESCE(?, price),
				    image_path = COALESCE(?, image_path),
				    sold_out = COALESCE(?, sold_out)
				WHERE id = ?
				""", request.name(), categoryId, request.price(), request.imagePath(), request.soldOut(), id);
		return Map.of("ok", true);
	}

	@PutMapping("/api/admin/products/{id}/sold-out")
	public Map<String, Object> soldOut(@PathVariable long id, @RequestBody SoldOutRequest request) {
		jdbcTemplate.update("UPDATE products SET sold_out = ? WHERE id = ?", request.soldOut(), id);
		return Map.of("ok", true);
	}

	@DeleteMapping("/api/admin/products/{id}")
	public Map<String, Object> deleteProduct(@PathVariable long id) {
		jdbcTemplate.update("UPDATE products SET active = FALSE WHERE id = ?", id);
		return Map.of("ok", true);
	}

	@GetMapping("/api/courses")
	public List<Map<String, Object>> courses() {
		return jdbcTemplate.queryForList("""
				SELECT id, name, price, duration, course_type FROM courses ORDER BY course_type
				""");
	}

	@GetMapping("/api/admin/courses/{courseId}/products")
	public List<Map<String, Object>> courseProducts(@PathVariable long courseId) {
		return jdbcTemplate.queryForList("""
				SELECT p.id, p.name, c.code AS category, c.name AS category_name,
				       EXISTS(SELECT 1 FROM course_products cp
				              WHERE cp.course_id = ? AND cp.product_id = p.id) AS included
				FROM products p
				JOIN categories c ON c.id = p.category_id
				WHERE p.active = TRUE AND c.active = TRUE AND c.code <> 'tabehoudai' AND p.store_id = ?
				ORDER BY c.display_order, p.id
				""", courseId, DEFAULT_STORE_ID);
	}

	@PostMapping("/api/admin/courses/{courseId}/products/{productId}")
	public Map<String, Object> addCourseProduct(@PathVariable long courseId, @PathVariable long productId) {
		jdbcTemplate.update(
				"INSERT IGNORE INTO course_products (course_id, product_id) VALUES (?, ?)", courseId, productId);
		return Map.of("ok", true);
	}

	@DeleteMapping("/api/admin/courses/{courseId}/products/{productId}")
	public Map<String, Object> removeCourseProduct(@PathVariable long courseId, @PathVariable long productId) {
		jdbcTemplate.update(
				"DELETE FROM course_products WHERE course_id = ? AND product_id = ?", courseId, productId);
		return Map.of("ok", true);
	}

	private String defaultImage(String imagePath) {
		return imagePath == null || imagePath.isBlank() ? "/images/product1.jpg" : imagePath;
	}

	private long resolveCategoryId(String code) {
		List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM categories WHERE code = ? AND active = TRUE AND code <> 'tabehoudai'", Long.class, code);
		if (ids.isEmpty()) {
			throw new IllegalArgumentException("カテゴリが見つかりません: " + code);
		}
		return ids.get(0);
	}

	private String normalizeName(String name) {
		if (name == null || name.trim().isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品名を入力してください。");
		}
		return name.trim();
	}

	private int requirePositivePrice(Integer price) {
		if (price == null || price <= 0) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品価格を入力してください。");
		}
		return price;
	}

	private Long firstLong(String sql, Object... args) {
		List<Long> ids = jdbcTemplate.queryForList(sql, Long.class, args);
		return ids.isEmpty() ? null : ids.get(0);
	}

	private long generatedProductId(GeneratedKeyHolder keyHolder) {
		Map<String, Object> keys = keyHolder.getKeys();
		if (keys != null) {
			Object id = keys.containsKey("id") ? keys.get("id") : keys.get("ID");
			if (id instanceof Number number) {
				return number.longValue();
			}
			if (keys.size() == 1) {
				Object onlyValue = keys.values().iterator().next();
				if (onlyValue instanceof Number number) {
					return number.longValue();
				}
			}
		}
		throw new IllegalStateException("商品IDを取得できませんでした。");
	}

	public record ProductRequest(String name, String category, Integer price, String imagePath, Boolean soldOut) {
	}

	public record SoldOutRequest(Boolean soldOut) {
	}
}
