package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductApiController {

	private final JdbcTemplate jdbcTemplate;

	public ProductApiController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@GetMapping("/api/products")
	public List<Map<String, Object>> products() {
		return jdbcTemplate.queryForList("""
				SELECT id, name, category, price, image_path, sold_out
				FROM products
				ORDER BY id
				""");
	}
}
