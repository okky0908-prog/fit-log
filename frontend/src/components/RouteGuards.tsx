import { Navigate, Outlet, useLocation, type Location } from 'react-router'
import { useAuth } from '../lib/auth/authContext'
import styles from './RouteGuards.module.css'

/** ログインの状態を確かめている間（開いた直後の再発行） */
export function AuthLoading() {
  return <output className={styles.loading}>読み込み中…</output>
}

/** ログインが必要な画面。未ログインならログイン画面へ移動し、ログインしたらこの画面に戻れるよう、もとの場所を渡す */
export function RequireAuth() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <AuthLoading />
  return user ? <Outlet /> : <Navigate to="/login" replace state={{ from: location }} />
}

/**
 * ログイン・新規登録の画面。ログイン済みならホームへ移動する。
 * ログインが必要な画面から来ていたら（RequireAuth が渡したもとの場所があれば）、そこへ戻る。ログインした直後もここで移動する
 */
export function PublicOnly() {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <AuthLoading />
  if (!user) return <Outlet />
  const from = (location.state as { from?: Location } | null)?.from
  return <Navigate to={from ? `${from.pathname}${from.search}${from.hash}` : '/'} replace />
}
