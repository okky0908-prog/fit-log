import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as api from '../api/client'
import type { LoginInput, Me, SignupInput } from '../api/types'
import { AuthContext, type AuthContextValue } from './authContext'

/**
 * ログインの状態（前回の SNS アプリと同じ）。
 * - 開いたら、リフレッシュトークン（HttpOnly の Cookie）でアクセストークンを再発行して、ログインの状態を戻す
 * - 再発行もできずに 401 になったら（Cookie の期限切れなど）、未ログインにする
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Me | null>(null)
  // Cookie は JavaScript から見えないので、ログインしているかは再発行を試すまで分からない
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    api.setUnauthorizedHandler(() => setUser(null))
    return () => api.setUnauthorizedHandler(null)
  }, [])

  useEffect(() => {
    let cancelled = false
    api
      .refresh()
      .then((res) => {
        if (!cancelled) setUser(res.user)
      })
      .catch(() => {
        // Cookie がない・無効・通信の失敗。どれも未ログインとして扱う
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const login = useCallback(async (input: LoginInput) => {
    setUser((await api.login(input)).user)
  }, [])

  const signup = useCallback(async (input: SignupInput) => {
    setUser((await api.signup(input)).user)
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } catch {
      // 通信に失敗しても、画面はログアウトする（リフレッシュトークンは期限で無効になる）
    }
    setUser(null)
  }, [])

  const loggedIn = user !== null
  const reloadUser = useCallback(async () => {
    if (loggedIn) {
      setUser(await api.fetchMe())
    }
  }, [loggedIn])

  const value = useMemo<AuthContextValue>(
    () => ({ user, loading, login, signup, logout, reloadUser }),
    [user, loading, login, signup, logout, reloadUser],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
