import { ref, shallowRef } from 'vue';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import type { ClientOptions } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { SSEClientTransport } from '@modelcontextprotocol/sdk/client/sse.js';
import type { CallToolResult, ListToolsResult } from '@modelcontextprotocol/sdk/types.js';

const CLIENT_OPTIONS: ClientOptions = { capabilities: { tools: {} } } as ClientOptions;

export type TransportType = 'streamable-http' | 'sse' | 'stdio';

export interface LogEntry {
  ts: string;
  type: 'info' | 'req' | 'res' | 'err' | 'sys';
  msg: string;
}

export function useMcpClient() {
  const client = shallowRef<Client | null>(null);
  const connected = ref(false);
  const error = ref('');
  const tools = ref<any[]>([]);
  const activeToolIdx = ref(-1);
  const logs = ref<LogEntry[]>([]);

  function addLog(type: LogEntry['type'], msg: string) {
    const ts = new Date().toLocaleTimeString();
    logs.value = [...logs.value, { ts, type, msg }];
  }

  function clearLogs() {
    logs.value = [];
  }

  async function connect(transportType: TransportType, url: string, prefix: string) {
    error.value = '';
    try {
      const base = url.replace(/\/$/, '');
      if (transportType === 'streamable-http') {
        const t = new StreamableHTTPClientTransport(new URL(`${base}/${prefix}/mcp`));
        t.onerror = (err) => addLog('err', `Transport error: ${err.message}`);
        const c = new Client({ name: 'mcp-gateway-test', version: '1.0.0' }, CLIENT_OPTIONS);
        addLog('sys', `Connecting to ${base}/${prefix}/mcp ...`);
        await c.connect(t);
        addLog('sys', `Connected, session: ${(t as any).sessionId || '(none)'}`);
        client.value = c;
      } else {
        const t = new SSEClientTransport(new URL(`${base}/${prefix}/sse`));
        const c = new Client({ name: 'mcp-gateway-test', version: '1.0.0' }, CLIENT_OPTIONS);
        await c.connect(t);
        client.value = c;
      }
      connected.value = true;
      addLog('sys', `Connected via ${transportType.toUpperCase()}`);
    } catch (err: any) {
      error.value = err.message;
      addLog('err', `Connect failed: ${err.message}`);
    }
  }

  async function disconnect() {
    if (client.value) {
      try { await client.value.close(); } catch (_) {}
      client.value = null;
    }
    connected.value = false;
    tools.value = [];
    activeToolIdx.value = -1;
    addLog('sys', 'Disconnected');
  }

  async function ping() {
    if (!client.value) return;
    try {
      await client.value.ping();
      addLog('res', 'Ping OK');
    } catch (err: any) {
      addLog('err', `Ping failed: ${err.message}`);
    }
  }

  async function listTools() {
    if (!client.value) return;
    try {
      const result = await client.value.listTools() as ListToolsResult;
      addLog('res', `Tools (${result.tools.length}):`);
      for (const tool of result.tools) {
        addLog('res', `  • ${tool.name} — ${tool.description || '(no description)'}`);
      }
      tools.value = result.tools as any[];
      if (tools.value.length > 0) {
        activeToolIdx.value = 0;
      }
    } catch (err: any) {
      addLog('err', `List tools failed: ${err.message}`);
    }
  }

  function selectTool(idx: number) {
    activeToolIdx.value = idx;
  }

  function schemaToTemplate(schema: any): string {
    if (!schema || schema.type !== 'object' || !schema.properties) return '{}';
    const props = schema.properties;
    const required: string[] = schema.required ?? [];
    const obj: Record<string, unknown> = {};
    for (const [key, prop] of Object.entries(props)) {
      const p = prop as any;
      if (p.default !== undefined) {
        obj[key] = p.default;
      } else if (p.type === 'string') {
        obj[key] = required.includes(key) ? '<string>' : '';
      } else if (p.type === 'number' || p.type === 'integer') {
        obj[key] = 0;
      } else if (p.type === 'boolean') {
        obj[key] = false;
      } else if (p.type === 'array') {
        obj[key] = [];
      } else if (p.type === 'object') {
        obj[key] = {};
      } else {
        obj[key] = null;
      }
    }
    return JSON.stringify(obj, null, 2);
  }

  async function callTool(name: string, argsStr: string) {
    if (!client.value) return;
    if (!name.trim()) { addLog('err', 'Tool name is required'); return; }

    let args: Record<string, unknown> = {};
    if (argsStr.trim()) {
      try { args = JSON.parse(argsStr); }
      catch { addLog('err', 'Invalid JSON arguments'); return; }
    }

    addLog('req', `Call: ${name}(${JSON.stringify(args)})`);
    try {
      const result = await client.value.callTool({ name, arguments: args }) as CallToolResult;
      const text = result.content
        .filter(c => c.type === 'text')
        .map(c => (c as any).text)
        .join('\n');
      addLog('res', `Result: ${text || '(empty)'}`);
    } catch (err: any) {
      addLog('err', `Call failed: ${err.message}`);
    }
  }

  return {
    client, connected, error, tools, activeToolIdx, logs,
    connect, disconnect, ping, listTools, selectTool, callTool,
    schemaToTemplate, addLog, clearLogs
  };
}
