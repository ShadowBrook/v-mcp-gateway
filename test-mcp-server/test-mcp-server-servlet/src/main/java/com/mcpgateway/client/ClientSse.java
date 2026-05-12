package com.mcpgateway.client;

import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;


public class ClientSse {

	public static void main(String[] args) {
		// 常规
		var transport1 = HttpClientSseClientTransport.builder("http://localhost:7080").build();
		new IpLocationQueryClient(transport1).run();

		System.out.println("==============================================================================");

		// 代理
		var transport2 = HttpClientSseClientTransport.builder("http://localhost:8080")
			.sseEndpoint("/sse-demo/sse")
			.build();
		new IpLocationQueryClient(transport2).run();
	}

}
