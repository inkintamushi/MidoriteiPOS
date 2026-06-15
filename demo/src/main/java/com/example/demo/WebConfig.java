package com.example.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final StaffAuthInterceptor staffAuthInterceptor;

	public WebConfig(StaffAuthInterceptor staffAuthInterceptor) {
		this.staffAuthInterceptor = staffAuthInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(staffAuthInterceptor)
				.addPathPatterns(
						"/taku.html",
						"/taioujoukyou.html",
						"/tyuumonn.html",
						"/rireki.html",
						"/mihaizen_rireki.html",
						"/kyakuannnai.html",
						"/shouhinnkannri.html",
						"/api/admin/**",
						"/api/staff/**");
	}
}
