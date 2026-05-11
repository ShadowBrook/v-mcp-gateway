/**
 * MCP Stdio Test Runner — Node.js only (uses child_process).
 *
 * Usage:
 *   npx tsx src/stdio-runner.ts <command> [args...]
 *
 * Examples:
 *   npx tsx src/stdio-runner.ts python -m my_mcp_server
 *   npx tsx src/stdio-runner.ts node ../path/to/mcp-server.js
 */

import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import type { ClientOptions } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

const CLIENT_OPTIONS: ClientOptions = { capabilities: { tools: {} } } as ClientOptions;

const args = process.argv.slice(2);
if (args.length === 0) {
  console.error('Usage: npx tsx src/stdio-runner.ts <command> [args...]');
  process.exit(1);
}

const [command, ...cmdArgs] = args;

console.log(`Starting stdio transport: ${command} ${cmdArgs.join(' ')}`);

const transport = new StdioClientTransport({ command, args: cmdArgs });

const client = new Client(
  { name: 'mcp-gateway-test-stdio', version: '1.0.0' },
  CLIENT_OPTIONS
);

async function run() {
  await client.connect(transport);
  console.log('Connected via stdio');

  await client.ping();
  console.log('Ping OK');

  const tools = await client.listTools();
  console.log(`\nTools (${tools.tools.length}):`);
  for (const tool of tools.tools) {
    console.log(`  • ${tool.name} — ${tool.description || '(no description)'}`);
  }

  // Call example tool if getLocationByIp exists
  const ipTool = tools.tools.find(t => t.name === 'getLocationByIp');
  if (ipTool) {
    console.log('\n--- Calling getLocationByIp("47.86.51.97") ---');
    const result = await client.callTool({ name: 'getLocationByIp', arguments: { ip: '47.86.51.97' } });
    // @ts-ignore
    const text = result.content
      .filter((c: any) => c.type === 'text')
      .map((c: any) => c.text)
      .join('\n');
    console.log('Result:', text || '(empty)');
  }

  await client.close();
  console.log('\nDone.');
}

run().catch(err => {
  console.error('Error:', err.message);
  process.exit(1);
});
