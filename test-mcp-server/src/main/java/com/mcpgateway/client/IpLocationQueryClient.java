package com.mcpgateway.client;

import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ListToolsResult;

public class IpLocationQueryClient {

	private final McpClientTransport transport;

	public IpLocationQueryClient(McpClientTransport transport) {
		this.transport = transport;
	}

	public void run() {

		var client = McpClient.sync(this.transport).build();

		client.initialize();

		client.ping();

		// List and demonstrate tools
		ListToolsResult toolsList = client.listTools();
		System.out.println("Available Tools = " + toolsList);
		toolsList.tools().stream().forEach(tool -> {
			System.out.println("Tool: " + tool.name() + ", description: " + tool.description() + ", schema: " + tool.inputSchema());
		});

		CallToolResult ipLocationResult = client.callTool(new CallToolRequest("getLocationByIp",
				Map.of("ip", "47.86.51.97")));
		System.out.println("ip所属地: " + ipLocationResult);

		client.closeGracefully();

	}

}
