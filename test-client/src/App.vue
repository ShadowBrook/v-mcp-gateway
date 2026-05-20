<template>
  <div id="app">
    <header>
      <h1>MCP Gateway Test Client</h1>
      <span class="badge" :class="statusClass">{{ statusText }}</span>
    </header>

    <main>
      <TransportConfig ref="transportRef" v-model="transportType" />

      <section class="card actions">
        <button class="btn primary" :disabled="mcp.connected.value" @click="onConnect">Connect</button>
        <button class="btn danger" :disabled="!mcp.connected.value" @click="mcp.disconnect()">Disconnect</button>
        <button class="btn" :disabled="!mcp.connected.value" @click="mcp.ping()">Ping</button>
        <button class="btn" :disabled="!mcp.connected.value" @click="mcp.listTools()">List Tools</button>
        <button class="btn" :disabled="!mcp.connected.value" @click="mcp.listPrompts()">List Prompts</button>
      </section>

      <ToolPanel
        :connected="mcp.connected.value"
        :tools="mcp.tools.value"
        :activeToolIdx="mcp.activeToolIdx.value"
        :schemaToTemplate="mcp.schemaToTemplate"
        @selectTool="mcp.selectTool"
        @call="mcp.callTool"
      />

      <PromptPanel
        :connected="mcp.connected.value"
        :prompts="mcp.prompts.value"
        :activePromptIdx="mcp.activePromptIdx.value"
        :promptArgsToTemplate="mcp.promptArgsToTemplate"
        @selectPrompt="mcp.selectPrompt"
        @getPrompt="mcp.getPrompt"
      />

      <LogPanel :logs="mcp.logs.value" @clear="mcp.clearLogs()" />
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import TransportConfig from './components/TransportConfig.vue';
import ToolPanel from './components/ToolPanel.vue';
import PromptPanel from './components/PromptPanel.vue';
import LogPanel from './components/LogPanel.vue';
import { useMcpClient, type TransportType } from './composables/useMcpClient';

const mcp = useMcpClient();
const transportType = ref<TransportType>('streamable-http');
const transportRef = ref<InstanceType<typeof TransportConfig>>();

const statusText = computed(() => {
  if (mcp.error.value) return 'Error';
  return mcp.connected.value ? 'Connected' : 'Disconnected';
});
const statusClass = computed(() => {
  if (mcp.error.value) return 'error';
  return mcp.connected.value ? 'connected' : 'disconnected';
});

async function onConnect() {
  if (transportType.value === 'stdio') {
    mcp.addLog('err', 'Stdio transport requires Node.js — run: npx tsx src/stdio-runner.ts');
    return;
  }
  const cfg = transportRef.value!;
  await mcp.connect(transportType.value, cfg.url, cfg.prefix);
}

mcp.addLog('sys', 'MCP Gateway Test Client ready');
mcp.addLog('sys', 'Streamable HTTP & SSE — browser  |  Stdio — run: npx tsx src/stdio-runner.ts');
</script>
