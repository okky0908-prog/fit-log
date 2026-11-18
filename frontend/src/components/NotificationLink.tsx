import styles from './NotificationLink.module.css'

type Props = {
  /** 未読の通知の数（A-121 の unreadCount。サーバーは 100 で数えるのをやめる） */
  count: number
}

/** これを超えたら「99+」と出す */
const MAX_SHOWN = 99

/**
 * ナビゲーションの「🔔 と未読の数」。押すと通知一覧（S-30）へ（docs/screens.md 共通部品・F-112）。
 * 0 なら数字を出さない。100 以上は「99+」。読み上げでは、絵文字と数字の代わりにリンクの名前（通知（未読 N件））を読む。
 * 画面の切り替え（React Router）を入れたら、a を Link に置き換える
 */
export function NotificationLink({ count }: Props) {
  const over = count > MAX_SHOWN
  const label = count === 0 ? '通知' : `通知（未読 ${over ? `${MAX_SHOWN}件以上` : `${count}件`}）`
  return (
    <a href="/notifications" aria-label={label} className={styles.link}>
      🔔
      {count > 0 && <span className={styles.count}>{over ? `${MAX_SHOWN}+` : count}</span>}
    </a>
  )
}
