import { ApiBusinessError } from '@/shared/api/http'
import type { DocsSyncStatus } from '@/shared/types/api.types'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import DocsSyncBar from './DocsSyncBar.vue'

const fetchDocsSyncStatus = vi.fn()
const startDocsSync = vi.fn()

vi.mock('@/shared/api/docs-api', () => ({
  fetchDocsSyncStatus: (...args: unknown[]) => fetchDocsSyncStatus(...args),
  startDocsSync: (...args: unknown[]) => startDocsSync(...args),
}))

function noneStatus(overrides: Partial<DocsSyncStatus> = {}): DocsSyncStatus {
  return {
    id: 0,
    startedAt: null,
    finishedAt: null,
    scanned: 0,
    added: 0,
    updated: 0,
    removed: 0,
    status: 'NONE',
    note: null,
    ...overrides,
  }
}

describe('DocsSyncBar', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    fetchDocsSyncStatus.mockReset()
    startDocsSync.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('NONE 状态渲染尚未同步', async () => {
    fetchDocsSyncStatus.mockResolvedValue(noneStatus())
    const wrapper = mount(DocsSyncBar)
    await flushPromises()
    expect(wrapper.text()).toContain('尚未同步')
    expect(wrapper.text()).toContain('NONE')
  })

  it('RUNNING 时按钮 disabled', async () => {
    fetchDocsSyncStatus.mockResolvedValue(noneStatus({ status: 'RUNNING', startedAt: '2026-11-24T10:00:00' }))
    const wrapper = mount(DocsSyncBar)
    await flushPromises()
    const button = wrapper.get('button')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.text()).toContain('同步中')
  })

  it('startDocsSync 抛 NOT_FOUND 时显示维护者提示', async () => {
    fetchDocsSyncStatus.mockResolvedValue(noneStatus())
    startDocsSync.mockRejectedValue(new ApiBusinessError('NOT_FOUND', '文档不存在'))
    const wrapper = mount(DocsSyncBar)
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('只有维护者可以触发同步')
    expect(wrapper.text()).not.toContain('文档不存在')
  })

  it('状态从 RUNNING 变 SUCCESS 后触发 synced', async () => {
    fetchDocsSyncStatus
      .mockResolvedValueOnce(noneStatus())
      .mockResolvedValueOnce(noneStatus({ status: 'RUNNING', startedAt: '2026-11-24T10:00:00' }))
      .mockResolvedValueOnce(
        noneStatus({
          id: 3,
          status: 'SUCCESS',
          startedAt: '2026-11-24T10:00:00',
          finishedAt: '2026-11-24T10:00:05',
          scanned: 10,
          updated: 1,
        }),
      )
    startDocsSync.mockResolvedValue({ runId: 3 })
    const wrapper = mount(DocsSyncBar)
    await flushPromises()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    await vi.advanceTimersByTimeAsync(0)
    await flushPromises()
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    await nextTick()
    expect(wrapper.emitted('synced')).toBeTruthy()
    expect(wrapper.text()).toContain('更新 1')
  })
})
