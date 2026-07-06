package com.example.demo;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 会計連携: 外部会計システムから呼び出されるAPI(機能統合版_v3.xlsx「会計連携」シート)。
 * 注文データ取得機能(getOrders)と会計情報更新機能(updateStatus)を提供する。
 */
@RestController
public class AccountingApiController {

	private static final long DEFAULT_STORE_ID = 1L;

	private final JdbcTemplate jdbcTemplate;

	public AccountingApiController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	// 注文データ取得機能(getOrders): 会計対象(未精算)の注文データを卓ごとに返す
	@GetMapping("/api/accounting/orders")
	public List<Map<String, Object>> getOrders(@RequestParam(required = false) Integer tableNumber) {
		if (tableNumber != null) {
			return jdbcTemplate.queryForList("""
					SELECT t.table_number, cg.id AS customer_group_id, cg.billing_status,
					       i.product_name AS name, i.quantity, i.canceled_quantity, i.unit_price
					FROM customer_groups cg
					JOIN table_sessions ts ON ts.customer_group_id = cg.id AND ts.ended_at IS NULL
					JOIN dining_tables t ON t.id = ts.table_id
					JOIN orders o ON o.table_session_id = ts.id
					JOIN order_items i ON i.order_id = o.id
					WHERE t.store_id = ? AND t.table_number = ?
					ORDER BY o.ordered_at, i.id
					""", DEFAULT_STORE_ID, tableNumber);
		}
		return jdbcTemplate.queryForList("""
				SELECT t.table_number, cg.id AS customer_group_id, cg.billing_status,
				       i.product_name AS name, i.quantity, i.canceled_quantity, i.unit_price
				FROM customer_groups cg
				JOIN table_sessions ts ON ts.customer_group_id = cg.id AND ts.ended_at IS NULL
				JOIN dining_tables t ON t.id = ts.table_id
				JOIN orders o ON o.table_session_id = ts.id
				JOIN order_items i ON i.order_id = o.id
				WHERE t.store_id = ?
				ORDER BY t.table_number, o.ordered_at, i.id
				""", DEFAULT_STORE_ID);
	}

	// 会計情報更新機能(updateStatus): billing_statusを更新する
	// 1 受付中, 2 会計済み, 4 未集金, 8 会計中
	@PutMapping("/api/accounting/status")
	public Map<String, Object> updateStatus(@RequestBody UpdateStatusRequest request) {
		List<Long> groupIds = jdbcTemplate.queryForList("""
				SELECT cg.id
				FROM customer_groups cg
				JOIN table_sessions ts ON ts.customer_group_id = cg.id AND ts.ended_at IS NULL
				JOIN dining_tables t ON t.id = ts.table_id
				WHERE t.store_id = ? AND t.table_number = ?
				""", Long.class, DEFAULT_STORE_ID, request.tableNumber());
		if (groupIds.isEmpty()) {
			return Map.of("ok", false, "message", "対象の卓が見つかりません。");
		}
		jdbcTemplate.update("UPDATE customer_groups SET billing_status = ? WHERE id = ?",
				request.billingStatus(), groupIds.get(0));
		return Map.of("ok", true);
	}

	public record UpdateStatusRequest(int tableNumber, int billingStatus) {
	}
}
