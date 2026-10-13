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
    <label class="profile-field">
      <span>个人概述</span>
      <textarea
        aria-label="个人概述"
        :value="modelValue.personalSummary"
        rows="4"
        placeholder="只保留你确认过的个人描述"
        @input="patch({ personalSummary: ($event.target as HTMLTextAreaElement).value })"
      />
    </label>
    <label class="profile-field">
      <span>核心技能 <small>使用逗号或换行分隔</small></span>
      <input
        aria-label="核心技能"
        :value="modelValue.coreSkills.join(', ')"
        placeholder="例如：Java, Spring Boot, Redis"
        @input="skills(($event.target as HTMLInputElement).value)"
      >
    </label>
    <section v-for="group in groups" :key="group.key" class="profile-group">
      <header>
        <div>
          <strong>{{ group.label }}</strong>
          <small>只填写真实且希望在面试中讨论的内容</small>
        </div>
        <button type="button" class="profile-group__add" @click="add(group.key)">+  添加</button>
      </header>
      <p v-if="!modelValue[group.key].length" class="empty">暂无内容，可手动添加。</p>
      <article v-for="(item, index) in modelValue[group.key]" :key="index" class="experience">
        <div class="experience__heading">
          <strong>{{ group.label }} {{ index + 1 }}</strong>
          <button type="button" class="remove" @click="remove(group.key, index)">移除</button>
        </div>
        <label>
          <span>名称</span>
          <input :value="item.name" placeholder="项目、公司或实习名称" @input="updateExperience(group.key, index, 'name', ($event.target as HTMLInputElement).value)">
        </label>
        <label>
          <span>职责或角色</span>
          <input :value="item.role" placeholder="你在其中负责什么" @input="updateExperience(group.key, index, 'role', ($event.target as HTMLInputElement).value)">
        </label>
        <label>
          <span>时间</span>
          <input :value="item.period" placeholder="例如：2025.06–2026.03" @input="updateExperience(group.key, index, 'period', ($event.target as HTMLInputElement).value)">
        </label>
        <label class="experience__wide">
          <span>经历描述</span>
          <textarea :value="item.description" rows="4" placeholder="描述你确认过的工作和结果" @input="updateExperience(group.key, index, 'description', ($event.target as HTMLTextAreaElement).value)" />
        </label>
        <label class="experience__wide">
          <span>技术栈</span>
          <input :value="item.technologies.join(', ')" placeholder="使用逗号分隔" @input="updateExperience(group.key, index, 'technologies', ($event.target as HTMLInputElement).value)">
        </label>
      </article>
    </section>
  </div>
</template>

<style scoped>
.profile-editor {
  display: grid;
  gap: 20px;
}

.profile-field,
.experience label {
  display: grid;
  gap: 7px;
  color: var(--ssm-color-ink);
  font-size: 14px;
  font-weight: 650;
}

.profile-field small {
  margin-left: 5px;
  color: var(--ssm-color-text-secondary);
  font-weight: 500;
}

.profile-editor input,
.profile-editor textarea {
  width: 100%;
  padding: 11px 13px;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-control);
  background: var(--ssm-color-paper);
  color: var(--ssm-color-ink);
  font: inherit;
}

.profile-editor textarea {
  line-height: 1.65;
  resize: vertical;
}

.profile-editor input:focus,
.profile-editor textarea:focus {
  border-color: var(--ssm-color-brand);
  outline: 3px solid color-mix(in srgb, var(--ssm-color-brand) 18%, transparent);
}

.profile-group {
  padding: 20px;
  border: 1px solid var(--ssm-color-line);
  border-radius: var(--ssm-radius-card);
  background: var(--ssm-color-paper);
}

.profile-group > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.profile-group > header div {
  display: grid;
  gap: 4px;
}

.profile-group > header small {
  color: var(--ssm-color-text-secondary);
  font-weight: 400;
}

.profile-group__add,
.remove {
  border: 0;
  background: transparent;
  cursor: pointer;
  font: inherit;
  font-weight: 650;
}

.profile-group__add {
  color: var(--ssm-color-brand);
}

.experience {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-top: 18px;
  padding-top: 18px;
  border-top: 1px solid var(--ssm-color-line);
}

.experience__heading,
.experience__wide {
  grid-column: 1 / -1;
}

.experience__heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.remove {
  color: var(--ssm-color-error);
  font-size: 13px;
}

.empty {
  margin: 16px 0 0;
  color: var(--ssm-color-text-secondary);
}

@media (max-width: 680px) {
  .experience {
    grid-template-columns: 1fr;
  }

  .experience__heading,
  .experience__wide {
    grid-column: 1;
  }
}
</style>
