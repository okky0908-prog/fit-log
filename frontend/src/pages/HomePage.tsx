import { useAuth } from '../lib/auth/authContext'
import styles from './HomePage.module.css'

/** S-10 ホーム。タイムラインは段階 1 のタイムラインの PR（docs/test-plan.md 8.2 の 12）で作る */
export function HomePage() {
  const { user } = useAuth()
  return (
    <>
      <h1 className={styles.title}>ホーム</h1>
      <p>ようこそ、{user?.displayName} さん</p>
    </>
  )
}
