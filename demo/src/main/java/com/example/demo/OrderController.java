package com.example.demo;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrderController {

	@GetMapping("/")
	public String index() {
		return "redirect:/order";
	}

	@GetMapping("/order")
	public String order() {
		return "order";
	}

	@GetMapping("/order_history")
	public String orderHistory() {
		return "order_history";
	}
}
