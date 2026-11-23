import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import MiniProgramEntryView from './MiniProgramEntryView.vue'
import trialCode from './bsr-wechat-trial-20261128.png'

afterEach(() => {
  vi.unstubAllEnvs()
  vi.restoreAllMocks()
})

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
    expect(wrapper.text()).not.toContain('打开调试')
    expect(wrapper.find('canvas').exists()).toBe(false)
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('does not render an unsafe or malformed image location', () => {
    vi.stubEnv('VITE_MINIPROGRAM_QR_IMAGE', 'javascript:alert(1)')
    const wrapper = mount(MiniProgramEntryView)
    expect(wrapper.find('img').exists()).toBe(false)
  })

  it('shows the November 28 trial code before its Beijing-time deadline', () => {
    vi.stubEnv('VITE_MINIPROGRAM_QR_IMAGE', undefined)
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-11-27T23:59:59+08:00'))
    const wrapper = mount(MiniProgramEntryView)
    expect(wrapper.get('img').attributes('src')).toBe(trialCode)
    expect(wrapper.text()).toContain('11 月 28 日前有效')
    expect(wrapper.text()).toContain('打开调试')
    expect(wrapper.text()).toContain('顾客账号')
  })

  it('hides the built-in trial code at its Beijing-time deadline', () => {
    vi.stubEnv('VITE_MINIPROGRAM_QR_IMAGE', undefined)
    vi.spyOn(Date, 'now').mockReturnValue(Date.parse('2026-11-28T00:00:00+08:00'))
    const wrapper = mount(MiniProgramEntryView)
    expect(wrapper.text()).toContain('暂未配置')
    expect(wrapper.text()).toContain('已到期')
    expect(wrapper.find('img').exists()).toBe(false)
  })
})
