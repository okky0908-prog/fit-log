import type { ReactNode } from 'react'
import { Link } from 'react-router'
import styles from './UserLink.module.css'

interface Props {
  username: string
  children: ReactNode
  className?: string
  /** アイコンのように、下線を出さないもの */
  plain?: boolean
}

/**
 * ユーザーへのリンク（docs/screens.md「ユーザーへのリンク（共通）」）。アイコン・表示名・@ユーザー名に使い、S-40 プロフィールへ移る
 */
export function UserLink({ username, children, className = '', plain = false }: Props) {
  return (
    <Link
      to={`/users/${encodeURIComponent(username)}`}
      className={`${plain ? styles.plain : styles.link} ${className}`}
    >
      {children}
    </Link>
  )
}
