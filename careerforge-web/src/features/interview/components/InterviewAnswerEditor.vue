<script setup lang="ts">
import { ref, watch } from 'vue'; import { Button } from 'ant-design-vue'
const props = defineProps<{ modelValue: string; frozen: boolean; saving: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value:string]; submit: []; skip: [] }>()
const local = ref(props.modelValue)
watch(() => props.modelValue, value => { if (value !== local.value) local.value = value })
watch(local, value => emit('update:modelValue', value))
</script>
<template><section class="editor"><textarea v-model="local" :disabled="frozen" maxlength="12000" rows="12" placeholder="结合你的真实项目、技术取舍和结果作答…"/><footer><span>{{ saving ? '正在保存草稿…' : `${local.length} / 12000` }}</span><div><Button :disabled="frozen || saving" @click="$emit('skip')">跳过此题</Button><Button type="primary" :disabled="frozen || saving || !local.trim()" @click="$emit('submit')">提交回答</Button></div></footer></section></template>
<style scoped>.editor{padding:22px;border:1px solid var(--cf-color-border);border-radius:16px;background:#fff}textarea{box-sizing:border-box;width:100%;padding:16px;border:1px solid #cbd5e1;border-radius:10px;resize:vertical;font:inherit;line-height:1.7}footer,footer div{display:flex;align-items:center;gap:10px}footer{justify-content:space-between;margin-top:12px;color:var(--cf-color-text-muted)}</style>
