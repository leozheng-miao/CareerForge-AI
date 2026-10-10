import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'

import TypewriterMarkdown from '@/components/content/TypewriterMarkdown.vue'

describe('TypewriterMarkdown', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('shows historical content immediately when playback is disabled', () => {
    const wrapper = mount(TypewriterMarkdown, {
      props: { content: '**已经保存的回答**', play: false },
    })

    expect(wrapper.text()).toBe('已经保存的回答')
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(false)
    expect(wrapper.emitted('complete')).toBeUndefined()
  })

  it('reveals a new answer progressively and completes once', async () => {
    vi.useFakeTimers()
    const answer = '这是一段需要以打字机方式逐步显示的 Career Coach 回答。'
    const wrapper = mount(TypewriterMarkdown, {
      props: { content: answer, play: true },
    })

    expect(wrapper.text().length).toBeGreaterThan(0)
    expect(wrapper.text().length).toBeLessThan(answer.length)
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(true)

    await vi.runAllTimersAsync()

    expect(wrapper.text()).toBe(answer)
    expect(wrapper.emitted('complete')).toHaveLength(1)
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(false)
  })

  it('does not animate when the user prefers reduced motion', () => {
    vi.spyOn(window, 'matchMedia').mockImplementation(
      (query: string) =>
        ({
          matches: query === '(prefers-reduced-motion: reduce)',
          media: query,
          onchange: null,
          addListener() {},
          removeListener() {},
          addEventListener() {},
          removeEventListener() {},
          dispatchEvent() {
            return false
          },
        }) as MediaQueryList,
    )

    const wrapper = mount(TypewriterMarkdown, {
      props: { content: '直接显示', play: true },
    })

    expect(wrapper.text()).toBe('直接显示')
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(false)
  })
})
