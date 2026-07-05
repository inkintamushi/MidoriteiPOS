package com.example.demo;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

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

	private static final long DEFAULT_STORE_ID = 1L;

	// 1 cleaning-unhandled(清掃未対応), 2 call-unhandled(呼出未対応),
	// 3 cleaning-needs-help(清掃要応援), 4 call-needs-help(呼出要応援),
	// 5 cleaning-in-progress(清掃対応中), 6 call-in-progress(呼出対応中),
	// 7 available(使用可能), 8 out-of-service(使用中止), 9 occupied(使用中)
	private static final Map<String, Integer> SEAT_STATUS_CODES = Map.ofEntries(
			Map.entry("CLEANING_UNHANDLED", 1),
			Map.entry("CALL_UNHANDLED", 2),
			Map.entry("CLEANING_NEEDS_HELP", 3),
			Map.entry("CALL_NEEDS_HELP", 4),
			Map.entry("CLEANING_IN_PROGRESS", 5),
			Map.entry("CALL_IN_PROGRESS", 6),
			Map.entry("AVAILABLE", 7),
			Map.entry("OUT_OF_SERVICE", 8),
			Map.entry("OCCUPIED", 9));

	private static final Map<Integer, String> SEAT_STATUS_LABELS = SEAT_STATUS_CODES.entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

	private final JdbcTemplate jdbcTemplate;

	public OrderApiController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostMapping("/api/orders")
	@Transactional
	public Map<String, Object> createOrder(@RequestBody CreateOrderRequest request) {
		long tableId = ensureTableId(request.tableNumber());
		long sessionId = ensureActiveTableSession(tableId, null);

		int total = 0;
		for (CreateOrderItem item : request.items()) {
			Map<String, Object> product = product(item.productId());
			total += ((Number) product.get("price")).intValue() * item.quantity();
		}

		long orderId = insertAndGetKey("""
				INSERT INTO orders (table_session_id, ordered_at)
				VALUES (?, CURRENT_TIMESTAMP)
				""", sessionId);

		for (CreateOrderItem item : request.items()) {
			Map<String, Object> product = product(item.productId());
			jdbcTemplate.update("""
					INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, status)
					VALUES (?, ?, ?, ?, ?, 'ORDERED')
					""", orderId, item.productId(), product.get("name"), item.quantity(), product.get("price"));
		}

		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
				SEAT_STATUS_CODES.get("OCCUPIED"), tableId);

		return Map.of("ok", true, "orderId", orderId, "totalPrice", total);
	}

	@GetMapping("/api/orders/history")
	public List<Map<String, Object>> history(@RequestParam(required = false) Integer tableNumber) {
		if (tableNumber == null) {
			return jdbcTemplate.queryForList("""
					SELECT o.id AS order_id, t.table_number, o.ordered_at AS created_at,
					       i.id AS item_id, i.product_name AS name, i.quantity AS qty,
					       i.delivered_quantity, i.canceled_quantity, i.unit_price, i.status AS item_status
					FROM orders o
					JOIN table_sessions ts ON ts.id = o.table_session_id
					JOIN dining_tables t ON t.id = ts.table_id
					JOIN order_items i ON i.order_id = o.id
					WHERE t.store_id = ?
					ORDER BY o.ordered_at DESC, i.id
					""", DEFAULT_STORE_ID);
		}
		return jdbcTemplate.queryForList("""
				SELECT o.id AS order_id, t.table_number, o.ordered_at AS created_at,
				       i.id AS item_id, i.product_name AS name, i.quantity AS qty,
				       i.delivered_quantity, i.canceled_quantity, i.unit_price, i.status AS item_status
				FROM orders o
				JOIN table_sessions ts ON ts.id = o.table_session_id
				JOIN dining_tables t ON t.id = ts.table_id
				JOIN order_items i ON i.order_id = o.id
				WHERE t.store_id = ? AND t.table_number = ?
				ORDER BY o.ordered_at DESC, i.id
				""", DEFAULT_STORE_ID, tableNumber);
	}

	@GetMapping("/api/staff/pending-orders")
	public List<Map<String, Object>> pendingOrders() {
		return jdbcTemplate.queryForList("""
				SELECT i.id, o.id AS order_id, t.table_number AS table_no, i.product_name AS item,
				       i.quantity AS qty, i.delivered_quantity, i.canceled_quantity,
				       (i.quantity - i.delivered_quantity - i.canceled_quantity) AS remaining_qty,
				       i.status, o.ordered_at AS created_at
				FROM order_items i
				JOIN orders o ON o.id = i.order_id
				JOIN table_sessions ts ON ts.id = o.table_session_id
				JOIN dining_tables t ON t.id = ts.table_id
				WHERE ts.ended_at IS NULL
				  AND i.status <> 'CANCELED'
				  AND (i.quantity - i.delivered_quantity - i.canceled_quantity) > 0
				ORDER BY o.ordered_at, i.id
				""");
	}

	@PutMapping("/api/staff/order-items/{itemId}/deliver")
	public Map<String, Object> deliver(@PathVariable long itemId) {
		jdbcTemplate.update("""
				UPDATE order_items
				SET delivered_quantity = quantity - canceled_quantity,
				    status = 'DELIVERED',
				    completed_at = CURRENT_TIMESTAMP
				WHERE id = ?
				""", itemId);
		return Map.of("ok", true);
	}

	@PutMapping("/api/staff/order-items/{itemId}/undeliver")
	public Map<String, Object> undeliver(@PathVariable long itemId) {
		jdbcTemplate.update("""
				UPDATE order_items
				SET delivered_quantity = 0,
				    status = 'ORDERED',
				    completed_at = NULL
				WHERE id = ?
				""", itemId);
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
		return Map.of("ok", true);
	}

	@PostMapping("/api/orders/pay")
	@Transactional
	public Map<String, Object> pay(@RequestBody PayRequest request) {
		Long tableId = findTableId(request.tableNumber());
		if (tableId == null) {
			return Map.of("ok", true, "totalPrice", 0);
		}

		List<Map<String, Object>> sessions = jdbcTemplate.queryForList("""
				SELECT id, customer_group_id
				FROM table_sessions
				WHERE table_id = ? AND ended_at IS NULL
				""", tableId);
		if (sessions.isEmpty()) {
			return Map.of("ok", true, "totalPrice", 0);
		}
		long sessionId = ((Number) sessions.get(0).get("id")).longValue();
		long groupId = ((Number) sessions.get(0).get("customer_group_id")).longValue();

		Integer total = jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(oi.unit_price * (oi.quantity - oi.canceled_quantity)), 0)
				FROM order_items oi
				JOIN orders o ON o.id = oi.order_id
				WHERE o.table_session_id = ?
				""", Integer.class, sessionId);

		jdbcTemplate.update("UPDATE table_sessions SET ended_at = CURRENT_TIMESTAMP WHERE id = ?", sessionId);
		jdbcTemplate.update("""
				UPDATE customer_groups SET billing_status = 2, left_at = CURRENT_TIMESTAMP WHERE id = ?
				""", groupId);
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
				SEAT_STATUS_CODES.get("AVAILABLE"), tableId);

		return Map.of("ok", true, "totalPrice", total);
	}

	@GetMapping("/api/staff/tables")
	public List<Map<String, Object>> tables() {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT t.table_number, COALESCE(cg.guest_count, 0) AS guest_count, t.seat_status
				FROM dining_tables t
				LEFT JOIN table_sessions ts ON ts.table_id = t.id AND ts.ended_at IS NULL
				LEFT JOIN customer_groups cg ON cg.id = ts.customer_group_id
				WHERE t.store_id = ?
				ORDER BY t.table_number
				""", DEFAULT_STORE_ID);
		List<Map<String, Object>> result = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			Map<String, Object> mapped = new LinkedHashMap<>(row);
			int code = ((Number) mapped.remove("seat_status")).intValue();
			mapped.put("status", SEAT_STATUS_LABELS.get(code));
			result.add(mapped);
		}
		return result;
	}

	@PutMapping("/api/staff/tables/{tableNumber}")
	public Map<String, Object> updateTable(@PathVariable int tableNumber, @RequestBody TableRequest request) {
		long tableId = ensureTableId(tableNumber);
		Integer seatStatus = request.status() == null ? null : resolveSeatStatus(request.status());
		if (seatStatus != null) {
			jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?", seatStatus, tableId);
		}

		if (seatStatus != null && seatStatus.equals(SEAT_STATUS_CODES.get("OCCUPIED"))) {
			ensureActiveTableSession(tableId, request.guestCount());
		} else if (request.guestCount() != null) {
			List<Long> activeGroupIds = jdbcTemplate.queryForList("""
					SELECT customer_group_id FROM table_sessions WHERE table_id = ? AND ended_at IS NULL
					""", Long.class, tableId);
			if (!activeGroupIds.isEmpty()) {
				jdbcTemplate.update("UPDATE customer_groups SET guest_count = ? WHERE id = ?",
						request.guestCount(), activeGroupIds.get(0));
			}
		}
		return Map.of("ok", true);
	}

	@PostMapping("/api/staff/tables/{tableNumber}/qr")
	public Map<String, Object> issueQr(@PathVariable int tableNumber, HttpServletRequest request) {
		long tableId = ensureTableId(tableNumber);
		long sessionId = ensureActiveTableSession(tableId, null);
		String token = UUID.randomUUID().toString();
		String secretCode = generateSecretCode();
		String orderUrl = baseUrl(request) + "/order?table=" + tableNumber + "&qr=" + token;
		jdbcTemplate.update("UPDATE table_sessions SET qr_code = ?, secret_code = ? WHERE id = ?",
				token, secretCode, sessionId);
		return Map.of("ok", true, "tableNumber", tableNumber, "token", token, "orderUrl", orderUrl);
	}

	private Map<String, Object> product(long productId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id, name, price
				FROM products
				WHERE id = ? AND store_id = ? AND active = TRUE AND sold_out = FALSE
				""", productId, DEFAULT_STORE_ID);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("商品が注文できません: " + productId);
		}
		return rows.get(0);
	}

	private long ensureTableId(int tableNumber) {
		List<Long> ids = jdbcTemplate.queryForList(
				"SELECT id FROM dining_tables WHERE store_id = ? AND table_number = ?",
				Long.class, DEFAULT_STORE_ID, tableNumber);
		if (!ids.isEmpty()) {
			return ids.get(0);
		}
		return insertAndGetKey("""
				INSERT INTO dining_tables (store_id, table_number, seat_status)
				VALUES (?, ?, ?)
				""", DEFAULT_STORE_ID, tableNumber, SEAT_STATUS_CODES.get("AVAILABLE"));
	}

	private Long findTableId(int tableNumber) {
		List<Long> ids = jdbcTemplate.queryForList(
				"SELECT id FROM dining_tables WHERE store_id = ? AND table_number = ?",
				Long.class, DEFAULT_STORE_ID, tableNumber);
		return ids.isEmpty() ? null : ids.get(0);
	}

	/**
	 * Finds the table's currently open session (customer_groups + table_sessions),
	 * or lazily creates one if the table has none. Mirrors the seating flow
	 * (kyakuannnai.html) and the ad-hoc table-occupation paths (createOrder,
	 * issueQr, updateTable) all landing on the same active session.
	 */
	private long ensureActiveTableSession(long tableId, Integer guestCount) {
		List<Long> activeSessionIds = jdbcTemplate.queryForList("""
				SELECT id FROM table_sessions WHERE table_id = ? AND ended_at IS NULL
				""", Long.class, tableId);
		if (!activeSessionIds.isEmpty()) {
			long sessionId = activeSessionIds.get(0);
			if (guestCount != null) {
				Long groupId = jdbcTemplate.queryForObject(
						"SELECT customer_group_id FROM table_sessions WHERE id = ?", Long.class, sessionId);
				jdbcTemplate.update("UPDATE customer_groups SET guest_count = ? WHERE id = ?", guestCount, groupId);
			}
			return sessionId;
		}

		int initialGuestCount = (guestCount != null && guestCount > 0) ? guestCount : 1;
		long groupId = insertAndGetKey("""
				INSERT INTO customer_groups (entered_at, billing_status, guest_count)
				VALUES (CURRENT_TIMESTAMP, 1, ?)
				""", initialGuestCount);
		long sessionId = insertAndGetKey("""
				INSERT INTO table_sessions (customer_group_id, table_id, started_at, qr_code, secret_code)
				VALUES (?, ?, CURRENT_TIMESTAMP, ?, ?)
				""", groupId, tableId, UUID.randomUUID().toString(), generateSecretCode());
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
				SEAT_STATUS_CODES.get("OCCUPIED"), tableId);
		return sessionId;
	}

	private String generateSecretCode() {
		return String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000));
	}

	private int resolveSeatStatus(String status) {
		Integer code = SEAT_STATUS_CODES.get(status);
		if (code == null) {
			throw new IllegalArgumentException("不明な座席状況です: " + status);
		}
		return code;
	}

	private long insertAndGetKey(String sql, Object... args) {
		GeneratedKeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			for (int i = 0; i < args.length; i++) {
				ps.setObject(i + 1, args[i]);
			}
			return ps;
		}, keyHolder);
		return keyHolder.getKey().longValue();
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
