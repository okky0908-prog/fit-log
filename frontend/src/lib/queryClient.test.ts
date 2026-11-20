import { expect, test } from 'vitest'
import { ApiError } from './api/client'
import { createQueryClient, shouldRetry } from './queryClient'

// データの取得に失敗したときのやり直し。一時的な失敗（通信・サーバーの 5xx）だけ、2回までやり直す。
// 入力・権限のエラー（4xx）は、やり直しても同じ結果なのでやり直さない

test.each([
  ['通信の失敗の1回目', 0, new ApiError(0, 'NETWORK_ERROR', ''), true],
  ['通信の失敗の2回目', 1, new ApiError(0, 'NETWORK_ERROR', ''), true],
  ['通信の失敗の3回目（もうやり直さない）', 2, new ApiError(0, 'NETWORK_ERROR', ''), false],
  ['503', 0, new ApiError(503, 'SERVICE_UNAVAILABLE', ''), true],
  ['500', 0, new ApiError(500, 'INTERNAL_ERROR', ''), true],
  ['499', 0, new ApiError(499, 'UNKNOWN', ''), false],
  ['400', 0, new ApiError(400, 'INVALID_TOKEN', ''), false],
  ['401', 0, new ApiError(401, 'SESSION_EXPIRED', ''), false],
  ['想定外の例外（画面の不具合）', 0, new Error('想定外'), false],
])('%s', (_, failureCount, error, expected) => {
  expect(shouldRetry(failureCount, error)).toBe(expected)
})

test('保存などの操作（mutation）はやり直さない（2回保存されないように）', () => {
  const client = createQueryClient()

  expect(client.getDefaultOptions().mutations?.retry).toBe(false)
  expect(client.getDefaultOptions().queries?.retry).toBe(shouldRetry)
})
