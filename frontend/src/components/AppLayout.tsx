import { Link, Outlet } from 'react-router'
import { useAuth } from '../lib/auth/authContext'
import styles from './AppLayout.module.css'
import { UnverifiedNotice } from './UnverifiedNotice'

/**
 * ログイン後の画面の共通の枠（docs/screens.md「ナビゲーション」）。
 * ホーム以外の行き先・通知・アイコンのメニューは、それぞれの画面を作るときに足す（ナビゲーションは docs/test-plan.md 8.2 の 10）
 */
export function AppLayout() {
  const { logout } = useAuth()
  return (
    <div className={styles.layout}>
      <header className={styles.header}>
        <Link to="/" className={styles.brand}>
          FIT LOG
        </Link>
        <button type="button" className="button-secondary" onClick={() => void logout()}>
          ログアウト
        </button>
      </header>
      <UnverifiedNotice />
      <main className={styles.main}>
        <Outlet />
      </main>
    </div>
  )
}
