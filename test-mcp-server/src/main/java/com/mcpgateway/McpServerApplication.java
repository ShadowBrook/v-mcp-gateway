package com.mcpgateway;

import com.mcpgateway.service.IpSearchService;
import com.mcpgateway.service.WeatherQueryService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(McpServerApplication.class, args);
	}

	@Bean
	public ToolCallbackProvider mcpTools(IpSearchService ipSearchService, WeatherQueryService weatherQueryService) {
		return MethodToolCallbackProvider.builder()
			.toolObjects(ipSearchService, weatherQueryService)
			.build();
	}

}
