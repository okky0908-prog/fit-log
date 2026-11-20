import { createContext, useContext } from 'react'
import type { LoginInput, Me, SignupInput } from '../api/types'

export interface AuthContextValue {
  /** ログイン中のユーザー。未ログインなら null */
  user: Me | null
  /** 開いたときにログインの状態を確かめて（Cookie で再発行して）いる間は true */
  loading: boolean
  login: (input: LoginInput) => Promise<void>
  /** 登録のあとは、そのままログイン中になる */
  signup: (input: SignupInput) => Promise<void>
  /** 通信に失敗しても、画面はログアウトの状態にする */
  logout: () => Promise<void>
  /** ログイン中のユーザーを取り直す（メールアドレスの確認が済んだあとなど）。未ログインなら何もしない */
  reloadUser: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext)
  if (!value) {
    throw new Error('useAuth は AuthProvider の中で使ってください')
  }
  return value
}
