package com.example.demo;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderApiController {

	private final JdbcTemplate jdbcTemplate;

	public OrderApiController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostMapping("/api/orders")
	@Transactional
	public Map<String, Object> createOrder(@RequestBody CreateOrderRequest request) {
		ensureTable(request.tableNumber());

		int total = 0;
		for (CreateOrderItem item : request.items()) {
			Map<String, Object> product = product(item.productId());
			total += ((Number) product.get("price")).intValue() * item.quantity();
		}
		int totalPrice = total;

		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO customer_orders (table_number, status, total_price)
					VALUES (?, 'ORDERED', ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setInt(1, request.tableNumber());
			ps.setInt(2, totalPrice);
			return ps;
		}, keyHolder);
		long orderId = keyHolder.getKey().longValue();

		for (CreateOrderItem item : request.items()) {
			Map<String, Object> product = product(item.productId());
			jdbcTemplate.update("""
					INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, status)
					VALUES (?, ?, ?, ?, ?, 'ORDERED')
					""", orderId, item.productId(), product.get("name"), item.quantity(), product.get("price"));
		}

		jdbcTemplate.update("""
				UPDATE dining_tables
				SET status = 'IN_USE', current_order_id = ?
				WHERE table_number = ?
				""", orderId, request.tableNumber());

		return Map.of("ok", true, "orderId", orderId, "totalPrice", total);
	}

	@GetMapping("/api/orders/history")
	public List<Map<String, Object>> history(@RequestParam(required = false) Integer tableNumber) {
		if (tableNumber == null) {
			return jdbcTemplate.queryForList("""
					SELECT o.id AS order_id, o.table_number, o.status AS order_status, o.total_price,
					       o.created_at, i.id AS item_id, i.product_name AS name, i.quantity AS qty,
					       i.delivered_quantity, i.canceled_quantity, i.unit_price, i.status AS item_status
					FROM customer_orders o
					JOIN order_items i ON i.order_id = o.id
					ORDER BY o.created_at DESC, i.id
					""");
		}
		return jdbcTemplate.queryForList("""
				SELECT o.id AS order_id, o.table_number, o.status AS order_status, o.total_price,
				       o.created_at, i.id AS item_id, i.product_name AS name, i.quantity AS qty,
				       i.delivered_quantity, i.canceled_quantity, i.unit_price, i.status AS item_status
				FROM customer_orders o
				JOIN order_items i ON i.order_id = o.id
				WHERE o.table_number = ?
				ORDER BY o.created_at DESC, i.id
				""", tableNumber);
	}

	@GetMapping("/api/staff/pending-orders")
	public List<Map<String, Object>> pendingOrders() {
		return jdbcTemplate.queryForList("""
				SELECT i.id, o.id AS order_id, o.table_number AS table_no, i.product_name AS item,
				       i.quantity AS qty, i.delivered_quantity, i.canceled_quantity,
				       (i.quantity - i.delivered_quantity - i.canceled_quantity) AS remaining_qty,
				       i.status, i.created_at
				FROM order_items i
				JOIN customer_orders o ON o.id = i.order_id
				WHERE o.status <> 'PAID'
				  AND i.status <> 'CANCELED'
				  AND (i.quantity - i.delivered_quantity - i.canceled_quantity) > 0
				ORDER BY i.created_at
				""");
	}

	@PutMapping("/api/staff/order-items/{itemId}/deliver")
	public Map<String, Object> deliver(@PathVariable long itemId) {
		jdbcTemplate.update("""
				UPDATE order_items
				SET delivered_quantity = quantity - canceled_quantity,
				    status = 'DELIVERED'
				WHERE id = ?
				""", itemId);
		refreshOrderStatus(itemId);
		return Map.of("ok", true);
	}

	@PutMapping("/api/staff/order-items/{itemId}/undeliver")
	public Map<String, Object> undeliver(@PathVariable long itemId) {
		jdbcTemplate.update("""
				UPDATE order_items
				SET delivered_quantity = 0,
				    status = 'ORDERED'
				WHERE id = ?
				""", itemId);
		Long orderId = jdbcTemplate.queryForObject("SELECT order_id FROM order_items WHERE id = ?", Long.class, itemId);
		jdbcTemplate.update("UPDATE customer_orders SET status = 'ORDERED' WHERE id = ? AND status <> 'PAID'", orderId);
		return Map.of("ok", true);
	}

	@PutMapping("/api/staff/order-items/{itemId}/cancel")
	public Map<String, Object> cancel(@PathVariable long itemId) {
		jdbcTemplate.update("""
				UPDATE order_items
				SET canceled_quantity = quantity - delivered_quantity,
				    status = 'CANCELED'
				WHERE id = ?
				""", itemId);
		refreshOrderStatus(itemId);
		return Map.of("ok", true);
	}

	@PostMapping("/api/orders/pay")
	@Transactional
	public Map<String, Object> pay(@RequestBody PayRequest request) {
		List<Map<String, Object>> orders = jdbcTemplate.queryForList("""
				SELECT id, total_price
				FROM customer_orders
				WHERE table_number = ? AND status <> 'PAID'
				""", request.tableNumber());
		int total = 0;
		for (Map<String, Object> order : orders) {
			long orderId = ((Number) order.get("id")).longValue();
			int orderTotal = ((Number) order.get("total_price")).intValue();
			total += orderTotal;
			jdbcTemplate.update("UPDATE customer_orders SET status = 'PAID', paid_at = CURRENT_TIMESTAMP WHERE id = ?", orderId);
			jdbcTemplate.update("""
					INSERT INTO sales_records (order_id, table_number, total_price)
					VALUES (?, ?, ?)
					ON DUPLICATE KEY UPDATE total_price = VALUES(total_price)
					""", orderId, request.tableNumber(), orderTotal);
		}
		jdbcTemplate.update("""
				UPDATE dining_tables
				SET status = 'EMPTY', guest_count = 0, current_order_id = NULL
				WHERE table_number = ?
				""", request.tableNumber());
		return Map.of("ok", true, "totalPrice", total);
	}

	@GetMapping("/api/staff/tables")
	public List<Map<String, Object>> tables() {
		return jdbcTemplate.queryForList("""
				SELECT table_number, guest_count, status, current_order_id
				FROM dining_tables
				ORDER BY table_number
				""");
	}

	@PutMapping("/api/staff/tables/{tableNumber}")
	public Map<String, Object> updateTable(@PathVariable int tableNumber, @RequestBody TableRequest request) {
		ensureTable(tableNumber);
		jdbcTemplate.update("""
				UPDATE dining_tables
				SET guest_count = COALESCE(?, guest_count),
				    status = COALESCE(?, status)
				WHERE table_number = ?
				""", request.guestCount(), request.status(), tableNumber);
		return Map.of("ok", true);
	}

	@PostMapping("/api/staff/tables/{tableNumber}/qr")
	public Map<String, Object> issueQr(@PathVariable int tableNumber, HttpServletRequest request) {
		ensureTable(tableNumber);
		String token = UUID.randomUUID().toString();
		String orderUrl = baseUrl(request) + "/order?table=" + tableNumber + "&qr=" + token;
		jdbcTemplate.update("UPDATE table_qr_codes SET active = FALSE WHERE table_number = ?", tableNumber);
		jdbcTemplate.update("""
				INSERT INTO table_qr_codes (table_number, qr_token, order_url, active)
				VALUES (?, ?, ?, TRUE)
				""", tableNumber, token, orderUrl);
		return Map.of("ok", true, "tableNumber", tableNumber, "token", token, "orderUrl", orderUrl);
	}

	private Map<String, Object> product(long productId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id, name, price
				FROM products
				WHERE id = ? AND active = TRUE AND sold_out = FALSE
				""", productId);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("商品が注文できません: " + productId);
		}
		return rows.get(0);
	}

	private void ensureTable(int tableNumber) {
		jdbcTemplate.update("""
				INSERT INTO dining_tables (table_number, guest_count, status)
				VALUES (?, 0, 'EMPTY')
				ON DUPLICATE KEY UPDATE table_number = VALUES(table_number)
				""", tableNumber);
	}

	private void refreshOrderStatus(long itemId) {
		Long orderId = jdbcTemplate.queryForObject("SELECT order_id FROM order_items WHERE id = ?", Long.class, itemId);
		Integer remaining = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM order_items
				WHERE order_id = ?
				  AND status <> 'CANCELED'
				  AND (quantity - delivered_quantity - canceled_quantity) > 0
				""", Integer.class, orderId);
		if (remaining != null && remaining == 0) {
			jdbcTemplate.update("UPDATE customer_orders SET status = 'DELIVERED' WHERE id = ? AND status <> 'PAID'", orderId);
		}
	}

	private String baseUrl(HttpServletRequest request) {
		String scheme = request.getScheme();
		int port = request.getServerPort();
		boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
		return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
	}

	public record CreateOrderRequest(int tableNumber, List<CreateOrderItem> items) {
	}

	public record CreateOrderItem(long productId, int quantity) {
	}

	public record PayRequest(int tableNumber) {
	}

	public record TableRequest(Integer guestCount, String status) {
	}
}
