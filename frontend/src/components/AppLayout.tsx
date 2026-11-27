import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { fetchUnreadCount } from '../lib/api/client'
import { useAuth } from '../lib/auth/authContext'
import { formatUnreadCount } from '../lib/notification/notification'
import { useRefreshTimeline } from '../lib/timeline/useRefreshTimeline'
import styles from './AppLayout.module.css'
import { Avatar } from './Avatar'
import { UnverifiedNotice } from './UnverifiedNotice'

/** 未読の数を取り直す間隔（F-112） */
const UNREAD_REFETCH_MS = 60_000

/**
 * ログイン後の画面の共通の枠（docs/screens.md「ナビゲーション」）。
 * パソコン（640px を超える幅）は上のヘッダーに、スマートフォンは下のタブバーに行き先を並べる（同じリンクを CSS で並べ替える）。
 *
 * 検索（S-11）・目標（S-23）と、アイコンのメニューのプロフィール・体重・テンプレート・フォローのリクエスト・設定は、
 * それぞれの画面を作るときに足す
 */
export function AppLayout() {
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <Link to="/" className={styles.brand}>
          FIT LOG
        </Link>
        <nav aria-label="メイン" className={styles.nav}>
          <HomeLink />
          <NavLink to="/workouts/new" className={`${styles.navLink} ${styles.record}`}>
            <span aria-hidden="true" className={styles.plus}>
              ＋
            </span>
            記録する
          </NavLink>
          {/* 週間（/records）・月間（/records/month）のどちらでも選ばれた見た目にする（NavLink は下の階層でも選ばれる） */}
          <NavLink to="/records" className={styles.navLink}>
            <span aria-hidden="true" className={styles.navIcon}>
              📊
            </span>
            記録
          </NavLink>
        </nav>
        <div className={styles.tools}>
          <NotificationBell />
          <AccountMenu />
        </div>
      </header>
      <UnverifiedNotice />
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  )
}

/** ホーム（S-10）。フォロー中・全体のどちらのタブでも選ばれた見た目にし、表示中にもう一度押すと、最新を取り直して一番上へ */
function HomeLink() {
  const { pathname } = useLocation()
  const refresh = useRefreshTimeline()
  const onHome = pathname === '/' || pathname === '/all'
  return (
    <NavLink
      to="/"
      className={() => `${styles.navLink} ${onHome ? 'active' : ''}`}
      aria-current={onHome ? 'page' : undefined}
      onClick={() => onHome && refresh()}
    >
      <span aria-hidden="true" className={styles.navIcon}>
        🏠
      </span>
      ホーム
    </NavLink>
  )
}

/**
 * 🔔 と未読の数。60秒ごとと、ブラウザのタブが表に戻ったときに取り直す（TanStack Query は、タブが表に戻ったとき
 * （visibilitychange）に古いデータを取り直す）。取れなくても、数を出さないだけにする
 */
function NotificationBell() {
  const unread = useQuery({
    queryKey: ['notifications', 'unread-count'],
    queryFn: fetchUnreadCount,
    refetchInterval: UNREAD_REFETCH_MS,
  })
  const count = unread.data?.unreadCount ?? 0
  const label = formatUnreadCount(count)
  return (
    <Link
      to="/notifications"
      className={styles.bell}
      aria-label={
        label === null ? '通知' : `通知（未読 ${label.endsWith('+') ? '99件以上' : `${count}件`}）`
      }
    >
      <span aria-hidden="true">🔔</span>
      {label && (
        <span aria-hidden="true" className={styles.badge}>
          {label}
        </span>
      )}
    </Link>
  )
}

/** 自分のアイコンのメニュー。今はログアウトだけ */
function AccountMenu() {
  const { user, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  // メニューの外を押したら閉じる
  useEffect(() => {
    if (!open) return
    function onPointerDown(event: PointerEvent) {
      if (!ref.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  return (
    <div ref={ref} className={styles.account}>
      <button
        type="button"
        className={styles.avatar}
        aria-label="自分のメニュー"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        {user && <Avatar displayName={user.displayName} iconUrl={user.iconUrl} size={32} />}
      </button>
      {open && (
        <ul role="menu" className={styles.menu}>
          {user && (
            <li role="none">
              <Link
                to={`/users/${encodeURIComponent(user.username)}`}
                role="menuitem"
                onClick={() => setOpen(false)}
              >
                プロフィール
              </Link>
            </li>
          )}
          <li role="none">
            <button type="button" role="menuitem" onClick={() => void logout()}>
              ログアウト
            </button>
          </li>
        </ul>
      )}
    </div>
  )
}
