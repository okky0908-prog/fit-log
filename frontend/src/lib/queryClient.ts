import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './api/client'

/** やり直す回数（最初の1回のほかに） */
const MAX_RETRIES = 2

/**
 * データの取得に失敗したとき、やり直すか。一時的な失敗（通信の失敗・サーバーの 5xx）だけ、2回までやり直す。
 * 入力・権限のエラー（4xx）と想定外の例外（画面の不具合）は、やり直しても同じ結果なのでやり直さない
 */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (failureCount >= MAX_RETRIES || !(error instanceof ApiError)) {
    return false
  }
  return error.status === 0 || error.status >= 500
}

/** サーバーのデータの管理（docs/tech-stack.md 3.5）。テストではテストごとに新しく作る */
export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: { retry: shouldRetry },
      // 保存などの操作は、やり直すと2回保存されることがあるので、やり直さない
      mutations: { retry: false },
    },
  })
}
