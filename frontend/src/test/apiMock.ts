import { vi } from 'vitest'
import type * as api from '../lib/api/client'

/**
 * 通信の部品の偽物。エラーの型（ApiError）などはそのまま使い、通信する関数だけを偽物にする。
 * vi.mock はファイルの先頭に移されるので、ほかの import より先に読めるよう、この関数は別のファイルに置いて動的に読む：
 *
 *   vi.mock('../lib/api/client', (original) => import('../test/apiMock').then((m) => m.mockApiClient(original)))
 */
export async function mockApiClient(importOriginal: () => Promise<typeof api>) {
  const actual = await importOriginal()
  return {
    ...actual,
    refresh: vi.fn<typeof actual.refresh>(),
    login: vi.fn<typeof actual.login>(),
    signup: vi.fn<typeof actual.signup>(),
    logout: vi.fn<typeof actual.logout>(),
    fetchMe: vi.fn<typeof actual.fetchMe>(),
    verifyEmail: vi.fn<typeof actual.verifyEmail>(),
    resendVerificationEmail: vi.fn<typeof actual.resendVerificationEmail>(),
    setUnauthorizedHandler: vi.fn<typeof actual.setUnauthorizedHandler>(),
    fetchWorkout: vi.fn<typeof actual.fetchWorkout>(),
    deleteWorkout: vi.fn<typeof actual.deleteWorkout>(),
  }
}
