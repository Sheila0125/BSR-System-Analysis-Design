import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MiniProgramEntryView from './MiniProgramEntryView.vue'

afterEach(() => vi.unstubAllEnvs())

describe('AC-WX05 configured mini-program entry', () => {
  it('explains how to obtain a code when none is configured', () => {
    vi.stubEnv('VITE_MINIPROGRAM_QR_IMAGE', '')
    const wrapper = mount(MiniProgramEntryView)
    expect(wrapper.text()).toContain('暂未配置')
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('displays only a configured static image', () => {
    vi.stubEnv('VITE_MINIPROGRAM_QR_IMAGE', '/assets/bsr-miniprogram-code.png')
    const wrapper = mount(MiniProgramEntryView)
    expect(wrapper.get('img').attributes('src')).toBe('/assets/bsr-miniprogram-code.png')
    expect(wrapper.find('canvas').exists()).toBe(false)
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('does not render an unsafe or malformed image location', () => {
    vi.stubEnv('VITE_MINIPROGRAM_QR_IMAGE', 'javascript:alert(1)')
    const wrapper = mount(MiniProgramEntryView)
    expect(wrapper.find('img').exists()).toBe(false)
  })
})
