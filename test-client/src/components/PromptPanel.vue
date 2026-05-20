<template>
  <section class="card" id="prompt-section" :class="{ visible: connected }">
    <h2>Prompt</h2>
    <div class="tool-tabs">
      <span v-if="prompts.length === 0" class="tool-tabs-empty">Click "List Prompts" to load</span>
      <button
        v-for="(p, i) in prompts"
        :key="p.name"
        class="tool-tab"
        :class="{ active: i === activePromptIdx }"
        :title="p.description || p.name"
        @click="selectPrompt(i)"
      >{{ p.name }}</button>
    </div>
    <div class="config-grid">
      <div class="field">
        <label for="prompt-name">Prompt Name</label>
        <input id="prompt-name" :value="currentPrompt?.name ?? ''" type="text" readonly placeholder="Select a prompt above" />
      </div>
      <div class="field">
        <label for="prompt-desc">Description</label>
        <input id="prompt-desc" :value="currentPrompt?.description ?? ''" type="text" readonly placeholder="" />
      </div>
      <div class="field">
        <label for="prompt-args">Arguments (JSON)</label>
        <textarea id="prompt-args" v-model="promptArgs" rows="3" placeholder=''></textarea>
      </div>
    </div>
    <button class="btn primary" :disabled="!connected" @click="$emit('getPrompt', currentPrompt?.name ?? '', promptArgs)">Get Prompt</button>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';

const props = defineProps<{
  connected: boolean;
  prompts: any[];
  activePromptIdx: number;
  promptArgsToTemplate: (args: any[] | undefined) => string;
}>();

const emit = defineEmits<{
  selectPrompt: [idx: number];
  getPrompt: [name: string, args: string];
}>();

const promptArgs = ref('');

const currentPrompt = computed(() => props.prompts[props.activePromptIdx] ?? null);

watch(() => props.activePromptIdx, (idx) => {
  if (idx >= 0 && props.prompts[idx]) {
    promptArgs.value = props.promptArgsToTemplate(props.prompts[idx].arguments);
  }
});

function selectPrompt(idx: number) {
  emit('selectPrompt', idx);
}
</script>
