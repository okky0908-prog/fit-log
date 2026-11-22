import { Link } from 'react-router'
import { useAuth } from '../lib/auth/authContext'
import styles from './HomePage.module.css'

/**
 * S-10 ホーム。タイムラインは段階 1 のタイムラインの PR（docs/test-plan.md 8.2 の 12）で作る。
 * 記録の作成への入口は、ナビゲーション（10）を作るまでここに置く
 */
export function HomePage() {
  const { user } = useAuth()
  return (
    <>
      <h1 className={styles.title}>ホーム</h1>
      <p>ようこそ、{user?.displayName} さん</p>
      <Link to="/workouts/new" className="button-primary">
        記録する
      </Link>
    </>
  )
}
