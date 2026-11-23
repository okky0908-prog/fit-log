import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { errorMessage, followUser, unfollowUser } from '../lib/api/client'
import type { FollowStatus } from '../lib/api/types'
import { followButtonLabel, unfollowConfirmation } from '../lib/follow/follow'
import { useToast } from '../lib/toast'
import { ConfirmDialog } from './ConfirmDialog'
import styles from './FollowButton.module.css'

interface Props {
  username: string
  /** 自分から相手へのフォローの状態 */
  status: FollowStatus
  /** 相手が鍵アカウントか（「フォローをリクエスト」と出す） */
  isPrivate: boolean
}

/**
 * フォローボタン（docs/screens.md「ユーザー一覧の行（共通）」・S-40、F-50・F-51・F-57）。自分自身には出さない（呼ぶ側で判定する）。
 *
 * - フォローしていなければ、押すとすぐフォロー（鍵アカウントならリクエスト）する
 * - フォロー中・リクエスト中は、押すと解除・取り消しを確かめる。フォロー中にマウスを乗せると「フォロー解除」と出す
 * - 送信中は押せない。終わったら、プロフィールと一覧を取り直す（数と、ほかの行のボタンも合わせるため）。失敗したらお知らせで理由を出す
 */
export function FollowButton({ username, status, isPrivate }: Props) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [confirming, setConfirming] = useState(false)
  const confirmation = unfollowConfirmation(status)

  const mutation = useMutation({
    mutationFn: () => (status === 'none' ? followUser(username) : unfollowUser(username)),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['users'] }),
    onError: (err) => toast(errorMessage(err)),
    onSettled: () => setConfirming(false),
  })

  return (
    <>
      <button
        type="button"
        className={status === 'none' ? styles.follow : styles.following}
        disabled={mutation.isPending}
        onClick={() => (confirmation ? setConfirming(true) : mutation.mutate())}
      >
        {status === 'following' ? (
          <>
            <span className={styles.label}>フォロー中</span>
            <span className={styles.hoverLabel} aria-hidden="true">
              フォロー解除
            </span>
          </>
        ) : (
          followButtonLabel(status, isPrivate)
        )}
      </button>
      {confirming && confirmation && (
        <ConfirmDialog
          title={confirmation.title}
          confirmLabel={confirmation.confirmLabel}
          busy={mutation.isPending}
          onConfirm={() => mutation.mutate()}
          onCancel={() => setConfirming(false)}
        />
      )}
    </>
  )
}
