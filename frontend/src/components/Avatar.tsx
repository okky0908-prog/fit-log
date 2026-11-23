import { useState } from 'react'
import styles from './Avatar.module.css'

interface Props {
  displayName: string
  /** アイコンの URL。null なら表示名の頭文字を丸の中に出す */
  iconUrl: string | null
  /** 直径（px） */
  size: number
}

/**
 * ユーザーのアイコン（docs/features.md「アイコン画像」）。丸く切り抜いて出す。
 * 読み上げでは、まわりの名前やリンクで誰かが分かるので、アイコン自体は読み上げない
 */
export function Avatar({ displayName, iconUrl, size }: Props) {
  // 読み込めなかった URL。そのときは壊れた画像の代わりに頭文字を出す（署名付き URL の期限切れなど）
  const [failedUrl, setFailedUrl] = useState<string | null>(null)
  const style = { width: size, height: size, fontSize: Math.round(size * 0.4) }
  if (iconUrl && iconUrl !== failedUrl) {
    return (
      <img
        className={styles.avatar}
        src={iconUrl}
        alt=""
        width={size}
        height={size}
        style={style}
        data-testid="avatar"
        onError={() => setFailedUrl(iconUrl)}
      />
    )
  }
  return (
    <span
      className={`${styles.avatar} ${styles.initial}`}
      aria-hidden="true"
      style={style}
      data-testid="avatar"
    >
      {[...displayName][0] ?? '?'}
    </span>
  )
}
