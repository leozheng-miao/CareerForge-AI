<script setup lang="ts">
import type { ConfirmedProfile, ProfileExperience } from '@/features/interview/api/interview-flow-api'

const props = defineProps<{ modelValue: ConfirmedProfile }>()
const emit = defineEmits<{ 'update:modelValue': [value: ConfirmedProfile] }>()

type ExperienceKey = 'projectExperiences' | 'workExperiences' | 'internshipExperiences'
const groups: Array<{ key: ExperienceKey; label: string }> = [
  { key: 'projectExperiences', label: '项目经历' },
  { key: 'workExperiences', label: '工作经历' },
  { key: 'internshipExperiences', label: '实习经历' },
]
const emptyExperience = (): ProfileExperience => ({ name: '', role: '', period: '', description: '', technologies: [] })
function patch(value: Partial<ConfirmedProfile>) { emit('update:modelValue', { ...props.modelValue, ...value }) }
function skills(value: string) { patch({ coreSkills: value.split(/[,，\n]/).map(v => v.trim()).filter(Boolean) }) }
function updateExperience(key: ExperienceKey, index: number, field: keyof ProfileExperience, value: string) {
  const items: ProfileExperience[] = props.modelValue[key].map((item, itemIndex) => {
    if (itemIndex !== index) return item
    if (field === 'technologies') return { ...item, technologies: value.split(/[,，]/).map(v => v.trim()).filter(Boolean) }
    if (field === 'name') return { ...item, name: value }
    if (field === 'role') return { ...item, role: value }
    if (field === 'period') return { ...item, period: value }
    return { ...item, description: value }
  })
  patchExperiences(key, items)
}
function patchExperiences(key: ExperienceKey, items: ProfileExperience[]) {
  if (key === 'projectExperiences') patch({ projectExperiences: items })
  else if (key === 'workExperiences') patch({ workExperiences: items })
  else patch({ internshipExperiences: items })
}
function add(key: ExperienceKey) { patchExperiences(key, [...props.modelValue[key], emptyExperience()]) }
function remove(key: ExperienceKey, index: number) { patchExperiences(key, props.modelValue[key].filter((_, i) => i !== index)) }
</script>

<template>
  <div class="profile-editor">
    <label>个人概述<textarea :value="modelValue.personalSummary" rows="3" @input="patch({ personalSummary: ($event.target as HTMLTextAreaElement).value })" /></label>
    <label>核心技能（逗号分隔）<input :value="modelValue.coreSkills.join(', ')" @input="skills(($event.target as HTMLInputElement).value)"></label>
    <section v-for="group in groups" :key="group.key">
      <header><strong>{{ group.label }}</strong><button type="button" @click="add(group.key)">添加</button></header>
      <p v-if="!modelValue[group.key].length" class="empty">暂无内容，可手动添加。</p>
      <article v-for="(item,index) in modelValue[group.key]" :key="index" class="experience">
        <input :value="item.name" placeholder="名称" @input="updateExperience(group.key,index,'name',($event.target as HTMLInputElement).value)">
        <input :value="item.role" placeholder="职责/角色" @input="updateExperience(group.key,index,'role',($event.target as HTMLInputElement).value)">
        <input :value="item.period" placeholder="时间" @input="updateExperience(group.key,index,'period',($event.target as HTMLInputElement).value)">
        <textarea :value="item.description" rows="3" placeholder="你确认的经历描述" @input="updateExperience(group.key,index,'description',($event.target as HTMLTextAreaElement).value)" />
        <input :value="item.technologies.join(', ')" placeholder="技术栈，逗号分隔" @input="updateExperience(group.key,index,'technologies',($event.target as HTMLInputElement).value)">
        <button type="button" class="remove" @click="remove(group.key,index)">移除</button>
      </article>
    </section>
  </div>
</template>

<style scoped>.profile-editor{display:grid;gap:18px}.profile-editor label{display:grid;gap:7px;font-weight:650}.profile-editor input,.profile-editor textarea{width:100%;padding:10px 12px;border:1px solid var(--cf-color-border);border-radius:8px;font:inherit}.profile-editor section{padding:16px;border:1px solid var(--cf-color-border);border-radius:12px}.profile-editor header{display:flex;justify-content:space-between}.experience{display:grid;grid-template-columns:1fr 1fr 1fr;gap:10px;margin-top:12px}.experience textarea{grid-column:1/-1}.remove{color:#b42318}.empty{color:var(--cf-color-text-muted)}</style>
