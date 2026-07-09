<template>
  <section class="card">
    <h2>Log</h2>
    <div id="log-container" ref="container">
      <div id="log">
        <div
          v-for="(entry, i) in logs"
          :key="i"
          class="log-entry"
          :class="[entry.type, { streaming: entry.streaming }]"
        >
          <span class="ts">{{ entry.ts }}</span>
          <div class="md-content" v-html="renderMarkdown(entry.msg)"></div>
          <span v-if="entry.streaming" class="stream-cursor">▋</span>
        </div>
      </div>
    </div>
    <div class="log-toolbar">
      <button class="btn" @click="$emit('clear')">Clear</button>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue';
import { marked } from 'marked';
import type { LogEntry } from '../composables/useMcpClient';

const props = defineProps<{ logs: LogEntry[] }>();
defineEmits<{ clear: [] }>();

const container = ref<HTMLElement>();

// Cache rendered HTML to avoid re-parsing on every re-render
const renderCache = new Map<string, string>();

function normalizeText(text: string): string {
  // Backend tools may return text with literal "\n" (two chars: backslash + n)
  // instead of actual newline characters. Normalize so marked can parse blocks.
  if (text.includes('\\n')) {
    text = text.replace(/\\n/g, '\n');
  }
  if (text.includes('\\t')) {
    text = text.replace(/\\t/g, '\t');
  }
  if (text.includes('\\r')) {
    text = text.replace(/\\r/g, '\r');
  }
  return text;
}

function renderMarkdown(msg: string): string {
  if (!msg) return '';
  const normalized = normalizeText(msg);
  let cached = renderCache.get(normalized);
  if (cached !== undefined) {
    return cached;
  }
  try {
    const result = marked.parse(normalized, { breaks: true, gfm: true });
    // marked.parse may return string or Promise<string> depending on config;
    // with default sync config it always returns string
    if (typeof result === 'string') {
      cached = result;
    } else {
      // Async mode — shouldn't happen with default options, but handle gracefully
      cached = `<pre>${escapeHtml(normalized)}</pre>`;
    }
  } catch (e) {
    // If marked fails, fall back to escaped text in <pre>
    cached = `<pre>${escapeHtml(normalized)}</pre>`;
  }
  renderCache.set(normalized, cached);
  // Prevent cache from growing indefinitely
  if (renderCache.size > 200) {
    const firstKey = renderCache.keys().next().value;
    if (firstKey !== undefined) renderCache.delete(firstKey);
  }
  return cached;
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

// Auto-scroll to bottom on new log entries or streaming updates
watch(() => props.logs.map(e => e.msg).join('\n'), async () => {
  await nextTick();
  if (container.value) {
    container.value.scrollTop = container.value.scrollHeight;
  }
});
</script>
