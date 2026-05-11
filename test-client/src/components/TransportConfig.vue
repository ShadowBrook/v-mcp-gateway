<template>
  <section class="card">
    <h2>Transport</h2>
    <div class="transport-tabs">
      <button
        v-for="t in transports"
        :key="t"
        class="tab"
        :class="{ active: modelValue === t }"
        @click="$emit('update:modelValue', t)"
      >{{ labels[t] }}</button>
    </div>

    <!-- HTTP config -->
    <div v-show="modelValue !== 'stdio'" class="config-grid">
      <div class="field">
        <label for="url">Gateway URL</label>
        <input id="url" v-model="url" type="text" placeholder="http://localhost:8080" />
      </div>
      <div class="field">
        <label for="prefix">Prefix (MCP Server name)</label>
        <input id="prefix" v-model="prefix" type="text" placeholder="test" />
      </div>
      <div class="field hint">
        <span>SSE: GET /:prefix/sse &nbsp;|&nbsp; Message: POST /:prefix/message</span>
        <span>Streamable HTTP: POST /:prefix/mcp</span>
      </div>
    </div>

    <!-- Stdio config -->
    <div v-show="modelValue === 'stdio'" class="config-grid">
      <div class="field">
        <label for="stdio-command">Command</label>
        <input id="stdio-command" v-model="stdio.command" type="text" placeholder="python" />
      </div>
      <div class="field">
        <label for="stdio-args">Arguments (comma separated)</label>
        <input id="stdio-args" v-model="stdio.args" type="text" placeholder="-m,my_mcp_server" />
      </div>
      <div class="field">
        <label for="stdio-env">Env (KEY=VAL, comma separated)</label>
        <input id="stdio-env" v-model="stdio.env" type="text" placeholder="PYTHONPATH=/opt/tools" />
      </div>
      <div class="field hint">
        <span>Stdio transport requires Node.js runtime (not available in browser)</span>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import type { TransportType } from '../composables/useMcpClient';

defineProps<{ modelValue: TransportType }>();
defineEmits<{ 'update:modelValue': [t: TransportType] }>();

const transports: TransportType[] = ['streamable-http', 'sse', 'stdio'];
const labels: Record<TransportType, string> = {
  'streamable-http': 'Streamable HTTP',
  'sse': 'SSE',
  'stdio': 'Stdio',
};

const url = ref('http://localhost:8080');
const prefix = ref('test');
const stdio = ref({ command: 'python', args: '-m,my_mcp_server', env: '' });

defineExpose({ url, prefix, stdio });
</script>
