import styles from './CommentCount.module.css'

/** 💬 とコメントの数（返信を含む）。0 なら数字を出さない。押してコメント欄へ移るのは 13-4 で足す */
export function CommentCount({ count }: { count: number }) {
  return (
    <span title="コメント" className={styles.count}>
      <span aria-hidden="true">💬</span>
      <span className="visually-hidden">コメント</span>
      {count > 0 && <span>{count}</span>}
    </span>
  )
}
