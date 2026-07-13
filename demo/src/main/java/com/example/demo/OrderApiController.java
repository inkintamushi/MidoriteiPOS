package com.example.demo;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class OrderApiController {

	private static final long DEFAULT_STORE_ID = 1L;

	// Timestamps are generated here in Java rather than via SQL CURRENT_TIMESTAMP
	// so they don't silently depend on the DB server's/OS's configured timezone
	// matching JST (e.g. a container or cloud host defaulting to UTC).
	private static final ZoneId JST = ZoneId.of("Asia/Tokyo");

	private static Timestamp nowJst() {
		return Timestamp.valueOf(LocalDateTime.now(JST));
	}

	// 1 cleaning-unhandled(清掃未対応), 2 call-unhandled(呼出未対応),
	// 3 cleaning-needs-help(清掃要応援), 4 call-needs-help(呼出要応援),
	// 5 cleaning-in-progress(清掃対応中), 6 call-in-progress(呼出対応中),
	// 7 available(使用可能), 8 out-of-service(使用中止), 9 occupied(使用中),
	// 10 payment-waiting(会計対応待ち)
	private static final Map<String, Integer> SEAT_STATUS_CODES = Map.ofEntries(
			Map.entry("CLEANING_UNHANDLED", 1),
			Map.entry("CALL_UNHANDLED", 2),
			Map.entry("CLEANING_NEEDS_HELP", 3),
			Map.entry("CALL_NEEDS_HELP", 4),
			Map.entry("CLEANING_IN_PROGRESS", 5),
			Map.entry("CALL_IN_PROGRESS", 6),
			Map.entry("AVAILABLE", 7),
			Map.entry("OUT_OF_SERVICE", 8),
			Map.entry("OCCUPIED", 9),
			Map.entry("PAYMENT_WAITING", 10));

	private static final Map<Integer, String> SEAT_STATUS_LABELS = SEAT_STATUS_CODES.entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

	private final JdbcTemplate jdbcTemplate;

	public OrderApiController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// Spring's default error page omits the exception message from the JSON
	// body unless server.error.include-message=always is set globally (which
	// would also leak unrelated 500s). Handling it here keeps the QR-rejection
	// message scoped to this controller while still reaching the customer UI.
	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<Map<String, Object>> handleResponseStatusException(ResponseStatusException ex) {
		return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", String.valueOf(ex.getReason())));
	}

	/**
	 * Customer-facing ordering endpoint, reached via the QR code printed/shown
	 * at seating time (kyakuannnai.html) or reissued from taku.html/tyuumonn.html.
	 * Requires the qr token to match the table's active session, otherwise anyone
	 * who merely knows the table number could order/checkout/call-staff on it.
	 */
	@PostMapping("/api/orders")
	@Transactional
	public Map<String, Object> createOrder(@RequestBody CreateOrderRequest request) {
		requireValidQr(request.tableNumber(), request.qrToken());
		return placeOrder(request.tableNumber(), request.items());
	}

	/**
	 * Staff-facing ordering endpoint (tyuumonn.html), used from trusted in-store
	 * devices that never have a QR token.
	 */
	@PostMapping("/api/staff/orders")
	@Transactional
	public Map<String, Object> createStaffOrder(@RequestBody CreateOrderRequest request) {
		return placeOrder(request.tableNumber(), request.items());
	}

	private Map<String, Object> placeOrder(int tableNumber, List<CreateOrderItem> items) {
		long tableId = ensureTableId(tableNumber);
		long sessionId = ensureActiveTableSession(tableId, null, null);
		Long courseId = currentCourseId(sessionId);

		for (CreateOrderItem item : items) {
			if (item.quantity() <= 0) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数量は1以上を指定してください。");
			}
		}

		long orderId = insertAndGetKey("""
				INSERT INTO orders (table_session_id, ordered_at)
				VALUES (?, ?)
				""", sessionId, nowJst());

		int total = 0;
		for (CreateOrderItem item : items) {
			Map<String, Object> product = product(item.productId());
			// 飲み放題コースの対象商品(course_products)は、コース料金に含まれるため個別課金しない
			boolean coveredByCourse = courseId != null && isCourseProduct(courseId, item.productId());
			int unitPrice = coveredByCourse ? 0 : ((Number) product.get("price")).intValue();
			total += unitPrice * item.quantity();
			jdbcTemplate.update("""
					INSERT INTO order_items (order_id, product_id, product_name, quantity, unit_price, status)
					VALUES (?, ?, ?, ?, ?, 'ORDERED')
					""", orderId, item.productId(), product.get("name"), item.quantity(), unitPrice);
		}
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
				SEAT_STATUS_CODES.get("OCCUPIED"), tableId);

		return Map.of("ok", true, "orderId", orderId, "totalPrice", total);
	}

	private Long currentCourseId(long sessionId) {
		return jdbcTemplate.queryForObject("""
				SELECT cg.course_id
				FROM table_sessions ts
				JOIN customer_groups cg ON cg.id = ts.customer_group_id
				WHERE ts.id = ?
				""", Long.class, sessionId);
	}

	private boolean isCourseProduct(long courseId, long productId) {
		Boolean exists = jdbcTemplate.queryForObject("""
				SELECT EXISTS(SELECT 1 FROM course_products WHERE course_id = ? AND product_id = ?)
				""", Boolean.class, courseId, productId);
		return Boolean.TRUE.equals(exists);
	}

	/**
	 * Confirms the qr token presented by the customer matches the table's
	 * currently active session. Without this, the token minted by issueQr was
	 * never actually checked anywhere, so QR issuance/reissuance had no effect:
	 * anyone who knew a table number could order, call staff, or trigger
	 * checkout for that table, and reissuing a QR never invalidated the old one.
	 */
	private void requireValidQr(int tableNumber, String qrToken) {
		Long tableId = findTableId(tableNumber);
		List<String> activeTokens = tableId == null
				? List.of()
				: jdbcTemplate.queryForList("""
						SELECT qr_code FROM table_sessions WHERE table_id = ? AND ended_at IS NULL
						""", String.class, tableId);
		if (activeTokens.isEmpty() || qrToken == null || !activeTokens.get(0).equals(qrToken)) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "QRコードが無効です。スタッフにお声がけください。");
		}
	}

	/**
	 * Returns how much course time is left. Elapsed time is computed in Java
	 * against started_at using the same JST clock that wrote it (nowJst()),
	 * rather than the DB server's own NOW()/CURRENT_TIMESTAMP - so this is
	 * correct even if MySQL's session/system timezone isn't JST. Previously
	 * this returned the raw started_at timestamp and let the browser compute
	 * the countdown by comparing it against the client device's own
	 * Date.now(); any skew between the client's clock/timezone and the
	 * server's meant the displayed remaining time could be wrong. Returning a
	 * relative "remainingSeconds" instead means the client only ever needs
	 * its clock's *rate* (always accurate), not its absolute value, to tick
	 * the countdown down.
	 */
	@GetMapping("/api/orders/session")
	public Map<String, Object> session(@RequestParam int tableNumber, @RequestParam(required = false) String qrToken) {
		requireValidQr(tableNumber, qrToken);
		Long tableId = findTableId(tableNumber);
		if (tableId == null) {
			return Map.of("hasCourse", false);
		}
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT ts.started_at, c.id AS course_id, c.name AS course_name, c.duration
				FROM table_sessions ts
				JOIN customer_groups cg ON cg.id = ts.customer_group_id
				JOIN courses c ON c.id = cg.course_id
				WHERE ts.table_id = ? AND ts.ended_at IS NULL
				""", tableId);
		if (rows.isEmpty()) {
			return Map.of("hasCourse", false);
		}
		Map<String, Object> row = rows.get(0);
		Integer durationMinutes = parseDurationMinutes((String) row.get("duration"));
		if (durationMinutes == null) {
			return Map.of("hasCourse", false);
		}
		LocalDateTime startedAt = toLocalDateTime(row.get("started_at"));
		long elapsedSeconds = Duration.between(startedAt, LocalDateTime.now(JST)).getSeconds();
		long remainingSeconds = Math.max(0, durationMinutes * 60L - elapsedSeconds);
		long courseId = ((Number) row.get("course_id")).longValue();
		List<Long> freeProductIds = jdbcTemplate.queryForList(
				"SELECT product_id FROM course_products WHERE course_id = ?", Long.class, courseId);
		return Map.of(
				"hasCourse", true,
				"courseId", courseId,
				"courseName", row.get("course_name"),
				"durationMinutes", durationMinutes,
				"remainingSeconds", remainingSeconds,
				"freeProductIds", freeProductIds,
				"includedProductIds", freeProductIds);
	}

	private static LocalDateTime toLocalDateTime(Object value) {
		if (value instanceof LocalDateTime localDateTime) {
			return localDateTime;
		}
		if (value instanceof Timestamp timestamp) {
			return timestamp.toLocalDateTime();
		}
		throw new IllegalStateException("Unexpected timestamp type: " + value.getClass());
	}

	private Integer parseDurationMinutes(String duration) {
		if (duration == null) {
			return null;
		}
		java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(duration);
		return matcher.find() ? Integer.parseInt(matcher.group()) : null;
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
				  AND i.quantity > i.canceled_quantity
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
				WHERE t.store_id = ? AND t.table_number = ? AND ts.ended_at IS NULL
				  AND i.quantity > i.canceled_quantity
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
				JOIN products p ON p.id = i.product_id
				JOIN categories c ON c.id = p.category_id
				WHERE ts.ended_at IS NULL
				  AND i.status <> 'CANCELED'
				  AND c.code <> 'nomi'
				  AND (i.quantity - i.delivered_quantity - i.canceled_quantity) > 0
				ORDER BY o.ordered_at, i.id
				""");
	}

	@PutMapping("/api/staff/order-items/{itemId}/deliver")
	@Transactional
	public Map<String, Object> deliver(@PathVariable long itemId, @RequestBody(required = false) QuantityRequest request) {
		Map<String, Object> item = orderItem(itemId);
		int quantity = ((Number) item.get("quantity")).intValue();
		int delivered = ((Number) item.get("delivered_quantity")).intValue();
		int canceled = ((Number) item.get("canceled_quantity")).intValue();
		int remaining = quantity - delivered - canceled;
		int requested = (request == null || request.quantity() == null) ? remaining : request.quantity();
		int applied = clamp(requested, remaining);
		int newDelivered = delivered + applied;

		if (newDelivered + canceled >= quantity) {
			jdbcTemplate.update("""
					UPDATE order_items
					SET delivered_quantity = ?, status = 'DELIVERED', completed_at = ?
					WHERE id = ?
					""", newDelivered, nowJst(), itemId);
		} else {
			jdbcTemplate.update("""
					UPDATE order_items
					SET delivered_quantity = ?, status = 'ORDERED', completed_at = NULL
					WHERE id = ?
					""", newDelivered, itemId);
		}
		return Map.of("ok", true);
	}

	@PutMapping("/api/staff/order-items/{itemId}/undeliver")
	@Transactional
	public Map<String, Object> undeliver(@PathVariable long itemId, @RequestBody(required = false) QuantityRequest request) {
		Map<String, Object> item = orderItem(itemId);
		int delivered = ((Number) item.get("delivered_quantity")).intValue();
		int requested = (request == null || request.quantity() == null) ? delivered : request.quantity();
		int applied = clamp(requested, delivered);
		int newDelivered = delivered - applied;

		jdbcTemplate.update("""
				UPDATE order_items
				SET delivered_quantity = ?, status = 'ORDERED', completed_at = NULL
				WHERE id = ?
				""", newDelivered, itemId);
		return Map.of("ok", true);
	}

	@PutMapping("/api/staff/order-items/{itemId}/cancel")
	@Transactional
	public Map<String, Object> cancel(@PathVariable long itemId, @RequestBody(required = false) QuantityRequest request) {
		Map<String, Object> item = orderItem(itemId);
		int quantity = ((Number) item.get("quantity")).intValue();
		int delivered = ((Number) item.get("delivered_quantity")).intValue();
		int canceled = ((Number) item.get("canceled_quantity")).intValue();
		int remaining = quantity - delivered - canceled;
		int requested = (request == null || request.quantity() == null) ? remaining : request.quantity();
		int applied = clamp(requested, remaining);
		int newCanceled = canceled + applied;

		jdbcTemplate.update("""
				UPDATE order_items
				SET canceled_quantity = ?, status = ?
				WHERE id = ?
				""", newCanceled, (newCanceled + delivered >= quantity) ? "CANCELED" : "ORDERED", itemId);
		return Map.of("ok", true);
	}

	private Map<String, Object> orderItem(long itemId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT quantity, delivered_quantity, canceled_quantity FROM order_items WHERE id = ?
				""", itemId);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("注文明細が見つかりません: " + itemId);
		}
		return rows.get(0);
	}

	private int clamp(int requested, int max) {
		return Math.max(0, Math.min(requested, max));
	}

	@PostMapping("/api/orders/checkout-call")
	public Map<String, Object> checkoutCall(@RequestBody PayRequest request) {
		requireValidQr(request.tableNumber(), request.qrToken());
		Long tableId = findTableId(request.tableNumber());
		if (tableId == null) {
			return Map.of("ok", true, "totalPrice", 0);
		}
		Integer total = currentSessionTotal(tableId);
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
				SEAT_STATUS_CODES.get("PAYMENT_WAITING"), tableId);
		return Map.of("ok", true, "totalPrice", total);
	}

	@PostMapping("/api/orders/call-staff")
	public Map<String, Object> callStaff(@RequestBody PayRequest request) {
		requireValidQr(request.tableNumber(), request.qrToken());
		Long tableId = findTableId(request.tableNumber());
		if (tableId != null) {
			jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
					SEAT_STATUS_CODES.get("CALL_UNHANDLED"), tableId);
		}
		return Map.of("ok", true);
	}

	@PutMapping("/api/staff/tables/{tableNumber}/finalize-payment")
	@Transactional
	public Map<String, Object> finalizePayment(@PathVariable int tableNumber) {
		Long tableId = findTableId(tableNumber);
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
		Integer total = sessionTotal(sessionId);

		Timestamp now = nowJst();
		jdbcTemplate.update("UPDATE table_sessions SET ended_at = ? WHERE id = ?", now, sessionId);
		jdbcTemplate.update("""
				UPDATE customer_groups SET billing_status = 2, left_at = ? WHERE id = ?
				""", now, groupId);
		// 状態遷移表: 会計時、自動で「清掃未対応」になる
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
				SEAT_STATUS_CODES.get("CLEANING_UNHANDLED"), tableId);

		return Map.of("ok", true, "totalPrice", total);
	}

	private Integer sessionTotal(long sessionId) {
		return jdbcTemplate.queryForObject("""
				SELECT COALESCE(SUM(oi.unit_price * (oi.quantity - oi.canceled_quantity)), 0)
				FROM order_items oi
				JOIN orders o ON o.id = oi.order_id
				WHERE o.table_session_id = ?
				""", Integer.class, sessionId);
	}

	private Integer currentSessionTotal(long tableId) {
		List<Long> sessionIds = jdbcTemplate.queryForList("""
				SELECT id FROM table_sessions WHERE table_id = ? AND ended_at IS NULL
				""", Long.class, tableId);
		if (sessionIds.isEmpty()) {
			return 0;
		}
		return sessionTotal(sessionIds.get(0));
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
	@Transactional
	public Map<String, Object> updateTable(@PathVariable int tableNumber, @RequestBody TableRequest request) {
		long tableId = ensureTableId(tableNumber);
		Integer seatStatus = request.status() == null ? null : resolveSeatStatus(request.status());
		if (seatStatus != null) {
			jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?", seatStatus, tableId);
		}

		if (seatStatus != null && seatStatus.equals(SEAT_STATUS_CODES.get("OCCUPIED"))) {
			ensureActiveTableSession(tableId, request.guestCount(), request.courseId());
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

	@PutMapping("/api/staff/tables/move")
	@Transactional
	public Map<String, Object> moveTable(@RequestBody MoveRequest request) {
		long fromId = ensureTableId(request.fromTableNumber());
		long toId = ensureTableId(request.toTableNumber());

		Long sessionId = activeSessionId(fromId);
		if (sessionId == null) {
			return Map.of("ok", false, "message", "移動元の卓に利用客がいません。");
		}
		if (activeSessionId(toId) != null) {
			return Map.of("ok", false, "message", "移動先の卓は空いていません。");
		}

		int fromStatus = jdbcTemplate.queryForObject(
				"SELECT seat_status FROM dining_tables WHERE id = ?", Integer.class, fromId);

		jdbcTemplate.update("UPDATE table_sessions SET table_id = ? WHERE id = ?", toId, sessionId);
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?", fromStatus, toId);
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
				SEAT_STATUS_CODES.get("AVAILABLE"), fromId);

		return Map.of("ok", true);
	}

	@PutMapping("/api/staff/tables/swap")
	@Transactional
	public Map<String, Object> swapTables(@RequestBody SwapRequest request) {
		long aId = ensureTableId(request.tableNumberA());
		long bId = ensureTableId(request.tableNumberB());

		Long aSessionId = activeSessionId(aId);
		Long bSessionId = activeSessionId(bId);
		if (aSessionId == null || bSessionId == null) {
			return Map.of("ok", false, "message", "交換する両方の卓が利用中である必要があります。");
		}

		int aStatus = jdbcTemplate.queryForObject("SELECT seat_status FROM dining_tables WHERE id = ?", Integer.class, aId);
		int bStatus = jdbcTemplate.queryForObject("SELECT seat_status FROM dining_tables WHERE id = ?", Integer.class, bId);

		// 一時的に重複しないよう、いったん退避用のtable_idを経由せず直接入れ替える
		// (aとbは別レコードなので、更新順序による衝突は発生しない)
		jdbcTemplate.update("UPDATE table_sessions SET table_id = ? WHERE id = ?", bId, aSessionId);
		jdbcTemplate.update("UPDATE table_sessions SET table_id = ? WHERE id = ?", aId, bSessionId);
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?", bStatus, aId);
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?", aStatus, bId);

		return Map.of("ok", true);
	}

	private Long activeSessionId(long tableId) {
		List<Long> ids = jdbcTemplate.queryForList(
				"SELECT id FROM table_sessions WHERE table_id = ? AND ended_at IS NULL", Long.class, tableId);
		return ids.isEmpty() ? null : ids.get(0);
	}

	/**
	 * Returns the table's current QR code, minting one only if its active
	 * session doesn't have one yet. Re-displaying (previously called
	 * "reissuing") must NOT replace an existing token: doing so silently broke
	 * any QR the customer already had in hand (printed slip, phone tab, etc.).
	 */
	@PostMapping("/api/staff/tables/{tableNumber}/qr")
	public Map<String, Object> issueQr(@PathVariable int tableNumber, HttpServletRequest request) {
		long tableId = ensureTableId(tableNumber);
		long sessionId = ensureActiveTableSession(tableId, null, null);
		String token = jdbcTemplate.queryForObject(
				"SELECT qr_code FROM table_sessions WHERE id = ?", String.class, sessionId);
		String orderUrl = baseUrl(request) + "/order?table=" + tableNumber + "&qr=" + token;
		return Map.of("ok", true, "tableNumber", tableNumber, "token", token, "orderUrl", orderUrl);
	}

	private Map<String, Object> product(long productId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT p.id, p.name, p.price, c.code AS category
				FROM products p
				JOIN categories c ON c.id = p.category_id
				WHERE p.id = ? AND p.store_id = ? AND p.active = TRUE AND p.sold_out = FALSE
				""", productId, DEFAULT_STORE_ID);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("商品が注文できません: " + productId);
		}
		return rows.get(0);
	}

	private boolean isIncludedInActiveCourse(long sessionId, long productId) {
		List<Integer> rows = jdbcTemplate.queryForList("""
				SELECT 1
				FROM table_sessions ts
				JOIN customer_groups cg ON cg.id = ts.customer_group_id
				JOIN course_products cp ON cp.course_id = cg.course_id
				WHERE ts.id = ? AND cp.product_id = ?
				LIMIT 1
				""", Integer.class, sessionId, productId);
		return !rows.isEmpty();
	}

	private Map<String, Object> selectedCourse(Map<String, Object> product, Long courseId) {
		if (courseId == null || !"nomi".equals(product.get("category"))) {
			return null;
		}
		List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
				SELECT id, name, price, duration
				FROM courses
				WHERE id = ?
				""", courseId);
		if (rows.isEmpty()) {
			throw new IllegalArgumentException("コースが選択できません: " + courseId);
		}
		return rows.get(0);
	}

	private void updateSessionCourse(long sessionId, long courseId) {
		Long groupId = jdbcTemplate.queryForObject(
				"SELECT customer_group_id FROM table_sessions WHERE id = ?", Long.class, sessionId);
		jdbcTemplate.update("UPDATE customer_groups SET course_id = ? WHERE id = ?", courseId, groupId);
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
	private long ensureActiveTableSession(long tableId, Integer guestCount, Long courseId) {
		List<Long> activeSessionIds = jdbcTemplate.queryForList("""
				SELECT id FROM table_sessions WHERE table_id = ? AND ended_at IS NULL
				""", Long.class, tableId);
		if (!activeSessionIds.isEmpty()) {
			long sessionId = activeSessionIds.get(0);
			if (guestCount != null || courseId != null) {
				Long groupId = jdbcTemplate.queryForObject(
						"SELECT customer_group_id FROM table_sessions WHERE id = ?", Long.class, sessionId);
				if (guestCount != null) {
					jdbcTemplate.update("UPDATE customer_groups SET guest_count = ? WHERE id = ?", guestCount, groupId);
				}
				if (courseId != null) {
					jdbcTemplate.update("UPDATE customer_groups SET course_id = ? WHERE id = ?", courseId, groupId);
				}
			}
			return sessionId;
		}

		int initialGuestCount = (guestCount != null && guestCount > 0) ? guestCount : 1;
		Timestamp now = nowJst();
		long groupId = insertAndGetKey("""
				INSERT INTO customer_groups (entered_at, billing_status, guest_count, course_id)
				VALUES (?, 1, ?, ?)
				""", now, initialGuestCount, courseId);
		long sessionId = insertAndGetKey("""
				INSERT INTO table_sessions (customer_group_id, table_id, started_at, qr_code, secret_code)
				VALUES (?, ?, ?, ?, ?)
				""", groupId, tableId, now, UUID.randomUUID().toString(), generateSecretCode());
		jdbcTemplate.update("UPDATE dining_tables SET seat_status = ? WHERE id = ?",
				SEAT_STATUS_CODES.get("OCCUPIED"), tableId);
		// 新規着席時にコースが選択されている場合、コース料金(人数分)を即時に計上する。
		// 配膳の必要がない金額のみの明細なので、最初からDELIVERED済みとして記録する。
		if (courseId != null) {
			placeCourseOrder(sessionId, courseId, initialGuestCount);
		}
		return sessionId;
	}

	private void placeCourseOrder(long sessionId, long courseId, int guestCount) {
		List<Map<String, Object>> courseRows = jdbcTemplate.queryForList(
				"SELECT name, price FROM courses WHERE id = ?", courseId);
		if (courseRows.isEmpty()) {
			return;
		}
		List<Long> planProductIds = jdbcTemplate.queryForList("""
				SELECT p.id FROM products p
				JOIN categories c ON c.id = p.category_id
				WHERE c.code = 'nomi' AND p.store_id = ? AND p.active = TRUE
				ORDER BY p.id
				""", Long.class, DEFAULT_STORE_ID);
		if (planProductIds.isEmpty()) {
			return;
		}

		Map<String, Object> course = courseRows.get(0);
		Timestamp now = nowJst();
		long orderId = insertAndGetKey("""
				INSERT INTO orders (table_session_id, ordered_at)
				VALUES (?, ?)
				""", sessionId, now);
		jdbcTemplate.update("""
				INSERT INTO order_items
					(order_id, product_id, product_name, quantity, delivered_quantity, unit_price, status, completed_at)
				VALUES (?, ?, ?, ?, ?, ?, 'DELIVERED', ?)
				""", orderId, planProductIds.get(0), course.get("name") + "プラン",
				guestCount, guestCount, course.get("price"), now);
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
				// setObject(index, null) is ambiguous without a type and some drivers
				// (H2, used by the local dev profile) reject it outright, unlike MySQL's
				// lenient handling. course_id is the first nullable arg this helper sees.
				if (args[i] == null) {
					ps.setNull(i + 1, java.sql.Types.NULL);
				} else {
					ps.setObject(i + 1, args[i]);
				}
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


	public record CreateOrderRequest(int tableNumber, String qrToken, List<CreateOrderItem> items) {
	}

	public record CreateOrderItem(long productId, int quantity, Long courseId) {
	}

	public record PayRequest(int tableNumber, String qrToken) {
	}

	public record TableRequest(Integer guestCount, String status, Long courseId) {
	}

	public record QuantityRequest(Integer quantity) {
	}

	public record MoveRequest(int fromTableNumber, int toTableNumber) {
	}

	public record SwapRequest(int tableNumberA, int tableNumberB) {
	}
}
