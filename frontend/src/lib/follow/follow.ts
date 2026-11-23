import type { FollowStatus } from '../api/types'

/*
 * フォローボタンの文言と、押したときの確認（docs/screens.md「ユーザー一覧の行（共通）」・S-40、F-50・F-51・F-57）
 */

/** ボタンの文言。鍵アカウントにはリクエストを出すので「フォローをリクエスト」 */
export function followButtonLabel(status: FollowStatus, isPrivate: boolean): string {
  switch (status) {
    case 'following':
      return 'フォロー中'
    case 'requested':
      return 'リクエスト中'
    default:
      return isPrivate ? 'フォローをリクエスト' : 'フォローする'
  }
}

/** 押したときの確認。フォロー中は解除、リクエスト中は取り消しを確かめる。フォローしていなければ確認しない（null） */
export function unfollowConfirmation(
  status: FollowStatus,
): { title: string; confirmLabel: string } | null {
  switch (status) {
    case 'following':
      return { title: 'フォローを解除しますか？', confirmLabel: '解除する' }
    case 'requested':
      return { title: 'リクエストを取り消しますか？', confirmLabel: '取り消す' }
    default:
      return null
  }
}
