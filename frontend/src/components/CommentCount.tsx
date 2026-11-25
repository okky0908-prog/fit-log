import { Link } from 'react-router'
import styles from './CommentCount.module.css'

interface Props {
  count: number
  /** 投稿カード：押すと投稿詳細へ移り、コメントの入力欄にカーソルを合わせる */
  workoutId?: number
  /** 投稿詳細：押すとコメントの入力欄にカーソルを合わせる */
  onFocusInput?: () => void
}

/** 💬 とコメントの数（返信を含む）。0 なら数字を出さない（docs/screens.md「投稿カード」） */
export function CommentCount({ count, workoutId, onFocusInput }: Props) {
  const content = (
    <>
      <span aria-hidden="true">💬</span>
      <span className="visually-hidden">コメント</span>
      {count > 0 && <span>{count}</span>}
    </>
  )
  // 読み上げの名前は、見えている数を単語として含める（WCAG 2.5.3。音声で操作する人が、見えている文字で押せるように）
  const label = count > 0 ? `${count} コメント欄へ` : 'コメント欄へ'
  if (onFocusInput) {
    return (
      <button
        type="button"
        title="コメント"
        aria-label={label}
        className={styles.action}
        onClick={onFocusInput}
      >
        {content}
      </button>
    )
  }
  if (workoutId !== undefined) {
    return (
      <Link
        to={`/workouts/${workoutId}`}
        state={{ focusComment: true }}
        title="コメント"
        aria-label={label}
        className={styles.action}
      >
        {content}
      </Link>
    )
  }
  return (
    <span title="コメント" className={styles.count}>
      {content}
    </span>
  )
}
