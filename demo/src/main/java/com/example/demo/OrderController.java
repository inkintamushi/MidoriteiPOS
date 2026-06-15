package com.example.demo;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrderController {

	@GetMapping("/")
	public String index() {
		return "switch";
	}

	@GetMapping("/switch")
	public String switchScreen() {
		return "switch";
	}

	@GetMapping("/order")
	public String order() {
		return "order";
	}

	@GetMapping("/order.html")
	public String orderHtml() {
		return "order";
	}

	@GetMapping("/order_codex")
	public String orderCodex() {
		return "order_codex";
	}

	@GetMapping("/order_codex.html")
	public String orderCodexHtml() {
		return "order_codex";
	}

	@GetMapping("/order_history")
	public String orderHistory() {
		return "order_history";
	}

	@GetMapping("/order_history.html")
	public String orderHistoryHtml() {
		return "order_history";
	}

	@GetMapping("/login.html")
	public String login() {
		return "login";
	}

	@GetMapping("/taku.html")
	public String taku() {
		return "taku";
	}

	@GetMapping("/tyuumonn.html")
	public String tyuumonn() {
		return "tyuumonn";
	}

	@GetMapping("/rireki.html")
	public String rireki() {
		return "rireki";
	}

	@GetMapping("/mihaizen_rireki.html")
	public String mihaizenRireki() {
		return "mihaizen_rireki";
	}

	@GetMapping("/kyakuannnai.html")
	public String kyakuannnai(Model model, HttpServletRequest request) {
		model.addAttribute("orderBaseUrl", resolveOrderBaseUrl(request));
		return "kyakuannnai";
	}

	@GetMapping("/shouhinnkannri.html")
	public String shouhinnkannri() {
		return "shouhinnkannri";
	}

	@GetMapping("/taioujoukyou.html")
	public String taioujoukyou() {
		return "taioujoukyou";
	}

	private String resolveOrderBaseUrl(HttpServletRequest request) {
		String scheme = request.getScheme();
		int port = request.getServerPort();
		String host = request.getServerName();

		if (isLoopbackHost(host)) {
			String lanAddress = findLanAddress();
			if (lanAddress != null) {
				host = lanAddress;
			}
		}

		boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
		return scheme + "://" + host + (defaultPort ? "" : ":" + port);
	}

	private boolean isLoopbackHost(String host) {
		return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
	}

	private String findLanAddress() {
		try {
			for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
				if (!networkInterface.isUp()
						|| networkInterface.isLoopback()
						|| networkInterface.isVirtual()
						|| isVirtualAdapterName(networkInterface)) {
					continue;
				}
				for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
					if (address instanceof Inet4Address && address.isSiteLocalAddress() && !address.isLoopbackAddress()) {
						return address.getHostAddress();
					}
				}
			}
		} catch (SocketException ex) {
			return null;
		}
		return null;
	}

	private boolean isVirtualAdapterName(NetworkInterface networkInterface) {
		String name = (networkInterface.getName() + " " + networkInterface.getDisplayName()).toLowerCase();
		return name.contains("virtualbox")
				|| name.contains("vmware")
				|| name.contains("hyper-v")
				|| name.contains("docker")
				|| name.contains("wsl");
	}
}
