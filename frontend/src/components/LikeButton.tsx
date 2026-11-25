import type { LikeState } from '../lib/api/types'
import { useToggleLike } from '../lib/like/useToggleLike'
import styles from './LikeButton.module.css'

interface Props extends LikeState {
  workoutId: number
}

/**
 * 💪 ナイストレーニングのボタン（docs/screens.md「投稿カード」、F-40〜F-42）。押すと付ける・取り消す。
 * 付けているときは色を変え、読み上げでは「押されている」と伝える（aria-pressed）。0 なら数字を出さない。送信中は押せない
 */
export function LikeButton({ workoutId, likeCount, likedByMe }: Props) {
  const mutation = useToggleLike(workoutId)
  return (
    <button
      type="button"
      title="ナイストレーニング"
      aria-pressed={likedByMe}
      className={likedByMe ? styles.liked : styles.button}
      disabled={mutation.isPending}
      onClick={() => mutation.mutate({ likeCount, likedByMe })}
    >
      <span aria-hidden="true">💪</span>
      <span className="visually-hidden">ナイストレーニング</span>
      {likeCount > 0 && <span>{likeCount}</span>}
    </button>
  )
}
