import { ref, shallowRef } from 'vue';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import type { ClientOptions } from '@modelcontextprotocol/sdk/client/index.js';
import { StreamableHTTPClientTransport } from '@modelcontextprotocol/sdk/client/streamableHttp.js';
import { SSEClientTransport } from '@modelcontextprotocol/sdk/client/sse.js';
import type { CallToolResult, ListToolsResult, ListPromptsResult, GetPromptResult } from '@modelcontextprotocol/sdk/types.js';

const CLIENT_OPTIONS: ClientOptions = { capabilities: { tools: {} } } as ClientOptions;

export type TransportType = 'streamable-http' | 'sse' | 'stdio';

export interface LogEntry {
  ts: string;
  type: 'info' | 'req' | 'res' | 'err' | 'sys';
  msg: string;
  streaming?: boolean;
}

export function useMcpClient() {
  const client = shallowRef<Client | null>(null);
  const connected = ref(false);
  const error = ref('');
  const tools = ref<any[]>([]);
  const activeToolIdx = ref(-1);
  const prompts = ref<any[]>([]);
  const activePromptIdx = ref(-1);
  const logs = ref<LogEntry[]>([]);
  const activeStreamIds = new Map<number, number>(); // streamId -> log index

  let streamIdCounter = 0;

  function addLog(type: LogEntry['type'], msg: string) {
    const ts = new Date().toLocaleTimeString();
    logs.value = [...logs.value, { ts, type, msg }];
  }

  function clearLogs() {
    logs.value = [];
    activeStreamIds.clear();
  }

  /** Create a streaming log entry; returns a streamId for later updates. */
  function startStreamLog(type: LogEntry['type'], msg: string): number {
    const id = ++streamIdCounter;
    const ts = new Date().toLocaleTimeString();
    logs.value = [...logs.value, { ts, type, msg, streaming: true }];
    activeStreamIds.set(id, logs.value.length - 1);
    return id;
  }

  /** Append text to a streaming log entry. */
  function appendStreamLog(id: number, chunk: string) {
    const idx = activeStreamIds.get(id);
    if (idx === undefined) return;
    const entry = logs.value[idx];
    if (!entry) return;
    // Mutate in-place then trigger reactivity via array spread
    entry.msg += chunk;
    logs.value = [...logs.value];
  }

  /** Finalize a streaming entry: replace content and mark as done. */
  function endStreamLog(id: number, finalMsg?: string) {
    const idx = activeStreamIds.get(id);
    if (idx === undefined) return;
    const entry = logs.value[idx];
    if (!entry) return;
    if (finalMsg !== undefined) {
      entry.msg = finalMsg;
    }
    entry.streaming = false;
    logs.value = [...logs.value];
    activeStreamIds.delete(id);
  }

  async function connect(transportType: TransportType, url: string, prefix: string) {
    error.value = '';
    try {
      const base = url.replace(/\/$/, '');
      const endpoint = prefix ? `${base}/${prefix}/mcp` : `${base}/mcp`;
      if (transportType === 'streamable-http') {
        const t = new StreamableHTTPClientTransport(new URL(endpoint));
        t.onerror = (err) => addLog('err', `Transport error: ${err.message}`);
        const c = new Client({ name: 'mcp-gateway-test', version: '1.0.0' }, CLIENT_OPTIONS);
        addLog('sys', `Connecting to ${endpoint} ...`);
        await c.connect(t);
        // Catch all server-pushed notifications (arrive via GET SSE stream)
        (c as any).fallbackNotificationHandler = async (n: any) => {
          if (n.method === 'notifications/progress') {
            const p = n.params?.progress ?? '?';
            const total = n.params?.total ?? '?';
            addLog('res', `> Progress: ${p}/${total}`);
          } else {
            addLog('res', `Server push: **${n.method}**\n\n\`\`\`json\n${JSON.stringify(n.params ?? {}, null, 2)}\n\`\`\``);
          }
        };
        addLog('sys', `Connected, session: \`${(t as any).sessionId || '(none)'}\``);
        client.value = c;
      } else {
        const sseEndpoint = prefix ? `${base}/${prefix}/sse` : `${base}/sse`;
        const t = new SSEClientTransport(new URL(sseEndpoint));
        t.onerror = (err) => addLog('err', `Transport error: ${err.message}`);
        const c = new Client({ name: 'mcp-gateway-test', version: '1.0.0' }, CLIENT_OPTIONS);
        await c.connect(t);
        (c as any).fallbackNotificationHandler = async (n: any) => {
          if (n.method === 'notifications/progress') {
            const p = n.params?.progress ?? '?';
            const total = n.params?.total ?? '?';
            addLog('res', `> Progress: ${p}/${total}`);
          } else {
            addLog('res', `Server push: **${n.method}**\n\n\`\`\`json\n${JSON.stringify(n.params ?? {}, null, 2)}\n\`\`\``);
          }
        };
        addLog('sys', `Connected via SSE, session: \`${(t as any).sessionId || '(none)'}\``);
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
    prompts.value = [];
    activePromptIdx.value = -1;
    addLog('sys', 'Disconnected');
  }

  async function ping() {
    if (!client.value) return;
    try {
      await client.value.ping();
      addLog('res', 'Ping OK ✅');
    } catch (err: any) {
      addLog('err', `Ping failed: ${err.message}`);
    }
  }

  async function listTools() {
    if (!client.value) return;
    try {
      const result = await client.value.listTools() as ListToolsResult;
      const lines: string[] = [`**Tools** (${result.tools.length}):`];
      for (const tool of result.tools) {
        lines.push(`- **${tool.name}** — ${tool.description || '*(no description)*'}`);
      }
      addLog('res', lines.join('\n'));
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

    addLog('req', `Call: **${name}**\n\`\`\`json\n${JSON.stringify(args, null, 2)}\n\`\`\``);

    // Start a streaming log entry — shows progress, then final result
    const streamId = startStreamLog('res', '⏳ Calling tool...');

    try {
      const result = await client.value.callTool(
        { name, arguments: args },
        undefined,
        {
          onprogress: (notification: any) => {
            const p = notification?.params?.progress ?? '?';
            const total = notification?.params?.total ?? '?';
            appendStreamLog(streamId, `\n\n> 📊 Progress: **${p}/${total}**`);
          },
        } as any,
      ) as CallToolResult;

      const text = result.content
        .filter(c => c.type === 'text')
        .map(c => (c as any).text)
        .join('\n');

      const md = result.isError
        ? `❌ **Tool Error**\n\n${text || '(empty)'}`
        : (text || '(empty)');
      endStreamLog(streamId, md);
    } catch (err: any) {
      endStreamLog(streamId, `❌ **Call failed**: ${err.message}`);
    }
  }

  async function listPrompts() {
    if (!client.value) return;
    try {
      const result = await client.value.listPrompts() as ListPromptsResult;
      const lines: string[] = [`**Prompts** (${result.prompts.length}):`];
      for (const p of result.prompts) {
        const argc = p.arguments?.length ?? 0;
        lines.push(`- **${p.name}** — ${p.description || '*(no description)*'} \`${argc} args\``);
      }
      addLog('res', lines.join('\n'));
      prompts.value = result.prompts as any[];
      if (prompts.value.length > 0) {
        activePromptIdx.value = 0;
      }
    } catch (err: any) {
      addLog('err', `List prompts failed: ${err.message}`);
    }
  }

  function selectPrompt(idx: number) {
    activePromptIdx.value = idx;
  }

  function promptArgsToTemplate(args: any[] | undefined): string {
    if (!args || args.length === 0) return '{}';
    const obj: Record<string, string> = {};
    for (const a of args) {
      obj[a.name] = a.required ? `<${a.name}>` : '';
    }
    return JSON.stringify(obj, null, 2);
  }

  async function getPrompt(name: string, argsStr: string) {
    if (!client.value) return;
    if (!name.trim()) { addLog('err', 'Prompt name is required'); return; }

    let args: Record<string, string> | undefined;
    if (argsStr.trim()) {
      try { args = JSON.parse(argsStr); }
      catch { addLog('err', 'Invalid JSON arguments'); return; }
    }

    addLog('req', `Get prompt: **${name}**${args ? `\n\`\`\`json\n${JSON.stringify(args, null, 2)}\n\`\`\`` : ''}`);
    try {
      const result = await client.value.getPrompt({ name, arguments: args }) as GetPromptResult;
      const lines: string[] = [];
      if (result.description) {
        lines.push(`> ${result.description}`);
        lines.push('');
      }
      lines.push(`**Messages** (${result.messages.length}):`);
      lines.push('');
      for (const msg of result.messages) {
        const content = msg.content as any;
        const text = content.type === 'text' ? content.text : `*[${content.type}]*`;
        lines.push(`**[${msg.role}]**`);
        lines.push('');
        lines.push(text);
        lines.push('');
        lines.push('---');
      }
      addLog('res', lines.join('\n'));
    } catch (err: any) {
      addLog('err', `Get prompt failed: ${err.message}`);
    }
  }

  return {
    client, connected, error, tools, activeToolIdx, prompts, activePromptIdx, logs,
    connect, disconnect, ping, listTools, selectTool, callTool,
    listPrompts, selectPrompt, getPrompt, promptArgsToTemplate,
    schemaToTemplate, addLog, clearLogs,
    startStreamLog, appendStreamLog, endStreamLog,
  };
}
