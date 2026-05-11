# v-mcp-gateway

MCP (Model Context Protocol) Gateway — 基于 Vert.x 的反向代理网关，将客户端请求路由到后端 MCP Server。

## 项目结构

```
v-mcp-gateway/
├── proxy-server/       # 核心网关模块 (Vert.x)
├── manage-server/      # 管理服务模块 (规划中，暂无代码)
├── test-mcp-server/    # 测试用 MCP Server (Spring Boot + Spring AI)
└── pom.xml             # 父 POM，Java 21, Vert.x 4.5.8
```

## proxy-server — 网关核心

### 架构概览

```
Client (MCP SDK)
    │
    ▼
┌──────────────────────────────────────────────┐
│  Router                                      │
│  GET  /health_check          → HealthHandler │
│  GET  /:prefix/sse           → SseHandler    │
│  GET  /sse?prefix=X          → SseHandler    │
│  POST /:prefix/message       → MessageHandler│
│  ANY  /:prefix/mcp           → McpHandler    │
└──────────────┬───────────────────────────────┘
               │
    ┌──────────▼──────────┐
    │   TransportFactory   │  ← 按 server type 创建 Transport
    └──────────┬──────────┘
               │
    ┌──────────┼──────────────────────┐
    ▼          ▼                      ▼
 SseTransport  StreamableHttpTransport  StdioTransport
 (HTTP SSE)    (HTTP POST/Response)     (子进程 stdin/stdout)
    │               │                      │
    ▼               ▼                      ▼
 Backend MCP     Backend MCP           本地子进程
 SSE Server      HTTP Server           MCP Server
```

### 三种 Transport

| Transport | 应用场景 | 说明 |
|---|---|---|
| `SseTransport` | 连接后端 SSE MCP Server | 建立长连接 SSE 流，通过 endpoint 事件获取消息 URL，POST 发送请求，响应通过 SSE 异步返回 |
| `StreamableHttpTransport` | 连接后端 Streamable HTTP MCP Server | 每次请求独立 POST，同步等待 JSON 响应返回 |
| `StdioTransport` | 连接本地子进程 MCP Server | 通过 ProcessBuilder 启动子进程，stdin/stdout 行分隔 JSON-RPC 通信 |

### 核心模块

| 包 | 类 | 职责 |
|---|---|---|
| `config` | `AppConfig`, `ServerConfig`, `ConfigLoader` | YAML 配置加载，支持 classpath 和文件系统 |
| `transport` | `Transport`, `TransportFactory`, `SseTransport`, `StreamableHttpTransport`, `StdioTransport` | 传输层抽象与实现 |
| `session` | `SessionStore`, `LocalSessionStore`, `GatewaySession` | SSE 会话管理（内存存储，支持 prefix 分组） |
| `handler` | `HealthHandler`, `SseHandler`, `MessageHandler`, `McpHandler` | HTTP 请求处理器 |
| `domain/mcp` | `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcError` | JSON-RPC 2.0 领域对象 |

### 配置示例 (application.yml)

```yaml
server:
  port: 8080

mcp:
  servers:
    - name: ip-location
      type: sse
      url: http://localhost:8081/sse

    - name: local-tool
      type: stdio
      command: python
      args: ["-m", "my_mcp_server"]
      env:
        PYTHONPATH: /opt/tools
      timeout: 30000
```

### 路由说明

- `GET /health_check` — 健康检查，返回 `{"status":"ok"}`
- `GET /:prefix/sse` — 建立 SSE 连接，prefix 来自路径参数
- `GET /sse?prefix=X` — 同上，prefix 来自查询参数
- `POST /:prefix/message` — 发送 JSON-RPC 消息到已建立 SSE 会话的后端
- `POST /:prefix/mcp` — Streamable HTTP 模式，直接转发 JSON-RPC 请求并同步返回响应


## 技术栈

- **Java 21**
- **Vert.x 4.5.8** — HTTP 服务、HTTP 客户端、JsonObject
- **SnakeYAML 2.2** — YAML 配置解析
- **Spring Boot 4.0 + Spring AI** — test-mcp-server
- **Maven** — 构建与模块管理

## 构建与运行

```bash
# 编译全部模块
mvn clean compile

# 运行网关
mvn exec:java -pl proxy-server \
  -Dexec.mainClass="com.mcpgateway.Main" \
  -Dexec.args="application.yml"

# 运行测试 MCP Server
mvn spring-boot:run -pl test-mcp-server
```
