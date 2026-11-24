import { useQueryClient } from '@tanstack/react-query'
import { useCallback } from 'react'

/**
 * タイムラインの最新を取り直して、一番上へ戻る（docs/screens.md S-10：新しい投稿のボタン・表示中のタブ・ナビの「ホーム」を押したとき）。
 * 読み込んだ続きのページは捨て、1ページ目から取り直す。新しい投稿の件数も一緒に取り直す
 */
export function useRefreshTimeline(): () => void {
  const queryClient = useQueryClient()
  return useCallback(() => {
    void queryClient.resetQueries({ queryKey: ['timeline'] })
    window.scrollTo({ top: 0 })
  }, [queryClient])
}
