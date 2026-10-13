import { describe, expect, it } from 'vitest'

import { mount } from '@vue/test-utils'
import App from '../App.vue'

describe('App', () => {
  it('provides the application shell and router outlet', () => {
    const wrapper = mount(App, {
      global: {
        stubs: {
          RouterView: {
            template: '<div data-testid="router-view" />',
          },
        },
      },
    })

    expect(wrapper.find('.shuishuimian-app').exists()).toBe(true)
    expect(wrapper.find('[data-testid="router-view"]').exists()).toBe(true)
  })
})
