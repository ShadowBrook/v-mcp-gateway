<template>
  <section class="card" id="tool-section" :class="{ visible: connected }">
    <h2>Tool Call</h2>
    <div class="tool-tabs">
      <span v-if="tools.length === 0" class="tool-tabs-empty">Click "List Tools" to load</span>
      <button
        v-for="(tool, i) in tools"
        :key="tool.name"
        class="tool-tab"
        :class="{ active: i === activeToolIdx }"
        :title="tool.description || tool.name"
        @click="selectTool(i)"
      >{{ tool.name }}</button>
    </div>
    <div class="config-grid">
      <div class="field">
        <label for="tool-name">Tool Name</label>
        <input id="tool-name" :value="currentTool?.name ?? ''" type="text" readonly placeholder="Select a tool above" />
      </div>
      <div class="field">
        <label for="tool-args">Arguments (JSON)</label>
        <textarea id="tool-args" v-model="toolArgs" rows="4" placeholder=''></textarea>
      </div>
    </div>
    <button class="btn primary" :disabled="!connected" @click="$emit('call', currentTool?.name ?? '', toolArgs)">Call Tool</button>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

const props = defineProps<{
  connected: boolean;
  tools: any[];
  activeToolIdx: number;
  schemaToTemplate: (schema: any) => string;
}>();

const emit = defineEmits<{
  selectTool: [idx: number];
  call: [name: string, args: string];
}>();

const toolArgs = ref('');

const currentTool = computed(() => props.tools[props.activeToolIdx] ?? null);

watch(() => props.activeToolIdx, (idx) => {
  if (idx >= 0 && props.tools[idx]) {
    toolArgs.value = props.schemaToTemplate(props.tools[idx].inputSchema);
  }
});

function selectTool(idx: number) {
  emit('selectTool', idx);
}
</script>
