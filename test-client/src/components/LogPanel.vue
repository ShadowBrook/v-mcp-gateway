<template>
  <section class="card">
    <h2>Log</h2>
    <div id="log-container" ref="container">
      <div id="log">
        <div v-for="(entry, i) in logs" :key="i" class="log-entry" :class="entry.type">
          <span class="ts">{{ entry.ts }}</span>{{ entry.msg }}
        </div>
      </div>
    </div>
    <button class="btn" @click="$emit('clear')">Clear</button>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import type { LogEntry } from '../composables/useMcpClient';

const props = defineProps<{ logs: LogEntry[] }>();
defineEmits<{ clear: [] }>();

const container = ref<HTMLElement>();

watch(() => props.logs.length, async () => {
  await nextTick();
  if (container.value) {
    container.value.scrollTop = container.value.scrollHeight;
  }
});
</script>
