import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ProfileDraftEditor from '@/features/interview/components/ProfileDraftEditor.vue'

const empty = { personalSummary: '', coreSkills: [], projectExperiences: [], workExperiences: [], internshipExperiences: [] }

describe('profile draft editor', () => {
  it('keeps missing categories editable and emits confirmed user changes', async () => {
    const wrapper = mount(ProfileDraftEditor, { props: { modelValue: empty } })
    expect(wrapper.text()).toContain('项目经历')
    expect(wrapper.text()).toContain('暂无内容，可手动添加')
    await wrapper.find('button').trigger('click')
    const emitted = wrapper.emitted('update:modelValue')?.[0]?.[0] as typeof empty
    expect(emitted.projectExperiences).toHaveLength(1)
  })
})
