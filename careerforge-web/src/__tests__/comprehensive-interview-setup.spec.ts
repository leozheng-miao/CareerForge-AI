import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { Select } from 'ant-design-vue'
import { nextTick } from 'vue'

import ProfileDraftEditor from '@/features/interview/components/ProfileDraftEditor.vue'
import ResumeUploadField from '@/features/interview/components/ResumeUploadField.vue'

const router = { push: vi.fn() }
vi.mock('vue-router', () => ({ useRouter: () => router, useRoute: () => ({ query: {} }) }))
vi.mock('@/features/interview/api/interview-flow-api', async original => ({
  ...await original<typeof import('@/features/interview/api/interview-flow-api')>(),
  createComprehensiveInterview: vi.fn().mockResolvedValue({ interviewId: 'c-1', mode: 'COMPREHENSIVE', stage: 'PREPARING', version: 0 }),
}))
import ComprehensiveInterviewSetupView from '@/views/app/ComprehensiveInterviewSetupView.vue'

describe('comprehensive interview setup', () => {
  beforeEach(() => { setActivePinia(createPinia()); router.push.mockReset() })
  it('requires JD and salary and offers no-resume path with all settings', async () => {
    const wrapper = mount(ComprehensiveInterviewSetupView, { global: { plugins: [createPinia()] } })
    expect(wrapper.text()).toContain('跳过简历')
    expect(wrapper.text()).toContain('60 分钟')
    expect(wrapper.text()).toContain('90 分钟')
    expect(wrapper.text()).toContain('挑战')
    const submit = wrapper.findAll('button').find(button => button.text().includes('确认并准备面试'))
    await submit?.trigger('click')
    await flushPromises()
    expect(router.push).not.toHaveBeenCalledWith(expect.stringContaining('/ready'))
  })

  it('uses a fixed monthly salary range dropdown', () => {
    const wrapper = mount(ComprehensiveInterviewSetupView, { global: { plugins: [createPinia()] } })
    const salary = wrapper.findComponent(Select)

    expect(salary.exists()).toBe(true)
    expect(salary.props('options')).toEqual(expect.arrayContaining([
      { label: '20K-30K/月', value: '20K-30K/月' },
      { label: '30K-40K/月', value: '30K-40K/月' },
      { label: '80K/月以上', value: '80K/月以上' },
    ]))
    expect(wrapper.find('input[placeholder="例如 30K-50K"]').exists()).toBe(false)
  })

  it('fills the candidate profile editor with the parsed resume result', async () => {
    const wrapper = mount(ComprehensiveInterviewSetupView, { global: { plugins: [createPinia()] } })
    const parsed = {
      personalSummary: 'Java Agent 后端开发者',
      coreSkills: ['Java', 'Redis'],
      projectExperiences: [{ name: 'CareerForge-AI', role: '后端开发', period: '2026', description: '面试工作流', technologies: ['Spring Boot'] }],
      workExperiences: [],
      internshipExperiences: [],
    }

    wrapper.findComponent(ResumeUploadField).vm.$emit('parsed', parsed)
    await nextTick()

    expect(wrapper.findComponent(ProfileDraftEditor).props('modelValue')).toEqual(parsed)
  })
})
