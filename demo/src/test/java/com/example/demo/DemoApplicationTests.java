package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class DemoApplicationTests {

	@Autowired
	ProductApiController productApiController;

	@Autowired
	OrderApiController orderApiController;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Test
	void contextLoads() {
	}

	@Test
	void addProductReactivatesDeletedProductWithSameName() {
		Map<String, Object> first = productApiController.addProduct(
				new ProductApiController.ProductRequest("再追加テスト商品", "yaki", 120, "/images/test-before.png", null));
		long productId = ((Number) first.get("id")).longValue();
		jdbcTemplate.update("INSERT INTO course_products (course_id, product_id) VALUES (?, ?)", 1L, productId);

		productApiController.deleteProduct(productId);
		Map<String, Object> reactivated = productApiController.addProduct(
				new ProductApiController.ProductRequest("再追加テスト商品", "dessert", 480, "/images/test-after.png", null));

		assertEquals(productId, ((Number) reactivated.get("id")).longValue());
		assertEquals(Boolean.TRUE, reactivated.get("reactivated"));
		assertTrue(jdbcTemplate.queryForObject("SELECT active FROM products WHERE id = ?", Boolean.class, productId));
		assertEquals(480, jdbcTemplate.queryForObject("SELECT price FROM products WHERE id = ?", Integer.class, productId));
		assertEquals("/images/test-after.png",
				jdbcTemplate.queryForObject("SELECT image_path FROM products WHERE id = ?", String.class, productId));
		assertEquals("dessert", jdbcTemplate.queryForObject("""
					SELECT c.code
					FROM products p
					JOIN categories c ON c.id = p.category_id
					WHERE p.id = ?
					""", String.class, productId));
		assertEquals(0, jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM course_products WHERE product_id = ?", Integer.class, productId));
	}

	@Test
	void addProductRejectsActiveDuplicateName() {
		productApiController.addProduct(
				new ProductApiController.ProductRequest("販売中重複テスト商品", "yaki", 120, "/images/test.png", null));

		ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> productApiController.addProduct(
				new ProductApiController.ProductRequest("販売中重複テスト商品", "yaki", 140, "/images/test2.png", null)));

		assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
	}

	@Test
	void issueQrRejectsAvailableTableWithoutCreatingSession() {
		long tableId = createTable(901, 7);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> orderApiController.issueQr(901, new MockHttpServletRequest()));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		assertEquals(0, activeSessionCount(tableId));
		assertEquals(7, seatStatus(tableId));
	}

	@Test
	void staffOrderRejectsGuestlessCleaningTableWithoutCreatingSession() {
		long tableId = createTable(902, 1);
		long productId = jdbcTemplate.queryForObject(
				"SELECT id FROM products WHERE store_id = 1 AND active = TRUE ORDER BY id LIMIT 1", Long.class);

		ResponseStatusException exception = assertThrows(ResponseStatusException.class,
				() -> orderApiController.createStaffOrder(new OrderApiController.CreateOrderRequest(
						902, null, List.of(new OrderApiController.CreateOrderItem(productId, 1, null)))));

		assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
		assertEquals(0, activeSessionCount(tableId));
		assertEquals(1, seatStatus(tableId));
	}

	@Test
	void moveTableRejectsGuestlessOccupiedTableWithoutCreatingSession() {
		long fromTableId = createTable(903, 9);
		long toTableId = createTable(904, 7);

		Map<String, Object> result = orderApiController.moveTable(new OrderApiController.MoveRequest(903, 904));

		assertEquals(false, result.get("ok"));
		assertEquals(0, activeSessionCount(fromTableId));
		assertEquals(0, activeSessionCount(toTableId));
		assertEquals(9, seatStatus(fromTableId));
		assertEquals(7, seatStatus(toTableId));
	}

	private long createTable(int tableNumber, int status) {
		jdbcTemplate.update("INSERT INTO dining_tables (store_id, table_number, seat_status) VALUES (1, ?, ?)",
				tableNumber, status);
		return jdbcTemplate.queryForObject(
				"SELECT id FROM dining_tables WHERE store_id = 1 AND table_number = ?", Long.class, tableNumber);
	}

	private int activeSessionCount(long tableId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM table_sessions WHERE table_id = ? AND ended_at IS NULL", Integer.class, tableId);
	}

	private int seatStatus(long tableId) {
		return jdbcTemplate.queryForObject("SELECT seat_status FROM dining_tables WHERE id = ?", Integer.class, tableId);
	}
}