import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import ProfileDraftEditor from '@/features/interview/components/ProfileDraftEditor.vue'

const empty = {
  personalSummary: '',
  coreSkills: [],
  projectExperiences: [],
  workExperiences: [],
  internshipExperiences: [],
}

describe('profile draft editor', () => {
  it('keeps missing categories editable and emits confirmed user changes', async () => {
    const wrapper = mount(ProfileDraftEditor, { props: { modelValue: empty } })
    expect(wrapper.text()).toContain('项目经历')
    expect(wrapper.text()).toContain('暂无内容，可手动添加')
    await wrapper.find('button').trigger('click')
    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as typeof empty
    expect(emitted.projectExperiences).toHaveLength(1)
  })

  it('emits edited summary and normalized skills without mutating the input object', async () => {
    const source = { ...empty, coreSkills: ['Java'] }
    const wrapper = mount(ProfileDraftEditor, { props: { modelValue: source } })

    await wrapper.get('textarea[aria-label="个人概述"]').setValue('后端与 Agent 开发')
    await wrapper.get('input[aria-label="核心技能"]').setValue('Java，Redis, Spring Boot')

    const values = wrapper.emitted('update:modelValue')?.map((event) => event[0]) as typeof source[]
    expect(values[values.length - 1]?.coreSkills).toEqual(['Java', 'Redis', 'Spring Boot'])
    expect(source).toEqual({ ...empty, coreSkills: ['Java'] })
  })
})
