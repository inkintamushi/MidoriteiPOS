package com.example.demo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import jakarta.servlet.http.HttpSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthApiController {

	public static final String STAFF_SESSION_KEY = "staffUser";

	private final JdbcTemplate jdbcTemplate;

	public AuthApiController(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@PostMapping("/api/auth/login")
	public Map<String, Object> login(@RequestBody LoginRequest request, HttpSession session) {
		var users = jdbcTemplate.queryForList("""
				SELECT staff_id, display_name, role
				FROM staff_users
				WHERE staff_id = ? AND password_hash = ? AND active = TRUE
				""", request.staffId(), sha256(request.password()));

		if (users.isEmpty()) {
			return Map.of("ok", false, "message", "ログインに失敗しました。");
		}

		Map<String, Object> user = users.get(0);
		session.setAttribute(STAFF_SESSION_KEY, user);
		return Map.of("ok", true, "user", user);
	}

	@PostMapping("/api/auth/logout")
	public Map<String, Object> logout(HttpSession session) {
		session.invalidate();
		return Map.of("ok", true);
	}

	@GetMapping("/api/auth/me")
	public Map<String, Object> me(HttpSession session) {
		Object user = session.getAttribute(STAFF_SESSION_KEY);
		return Map.of("ok", user != null, "user", user == null ? Map.of() : user);
	}

	public record LoginRequest(String staffId, String password) {
	}

	private String sha256(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder builder = new StringBuilder();
			for (byte b : hash) {
				builder.append(String.format("%02x", b));
			}
			return builder.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
	}
}
