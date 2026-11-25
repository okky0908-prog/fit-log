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
  const label = `コメント欄へ（${count}件）`
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
