import { useQuery } from '@tanstack/react-query'
import { useEffect } from 'react'
import { Link, useSearchParams } from 'react-router'
import { AuthLoading } from '../components/RouteGuards'
import { ApiError, errorMessage, verifyEmail } from '../lib/api/client'
import type { VerifyEmailResult } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { useResendVerification } from '../lib/auth/useResendVerification'
import styles from './AuthPage.module.css'

/**
 * トークンを含む URL が、リンク先などのほかのサイトに渡らないようにする（docs/screens.md「画面一覧」の下の決まり）。
 * この画面を開いている間だけ、ページの Referrer-Policy を no-referrer にする
 */
function useNoReferrer() {
  useEffect(() => {
    const meta = document.createElement('meta')
    meta.name = 'referrer'
    meta.content = 'no-referrer'
    document.head.append(meta)
    return () => meta.remove()
  }, [])
}

function successMessage(result: VerifyEmailResult): string {
  return result.result === 'email_changed'
    ? 'メールアドレスを変更しました'
    : 'メールアドレスの確認が済みました'
}

/**
 * S-05 メールアドレスの確認（docs/screens.md）。開くとすぐに A-06 を呼び、結果を出す。ログインしていてもしていなくても開ける。
 *
 * トークンは1回しか使えないので、同じトークンの呼び出しは TanStack Query で1回にまとめ（開発時の作り直しでも2回送らない）、
 * 自動のやり直しもしない（1回目が届いていたら、やり直しは「使えない」になって、利用者を迷わせるため）
 */
export function VerifyEmailPage() {
  useNoReferrer()
  const token = useSearchParams()[0].get('token')
  const { user, loading, reloadUser } = useAuth()
  const resend = useResendVerification()
  const verification = useQuery({
    queryKey: ['email-verification', token],
    queryFn: () => verifyEmail(token ?? ''),
    enabled: token !== null && token !== '',
    retry: false,
    staleTime: Infinity,
    refetchOnWindowFocus: false,
  })

  // ログイン中なら、確認済みになったユーザーを取り直す（未確認の案内を消すため）
  const verified = verification.isSuccess
  useEffect(() => {
    if (verified) void reloadUser()
  }, [verified, reloadUser])

  const invalid =
    !token ||
    (verification.error instanceof ApiError && verification.error.code === 'INVALID_TOKEN')

  let body
  if (invalid) {
    body = (
      <>
        <p className={styles.message}>このリンクは使えません</p>
        {user && !user.emailVerified && (
          <button
            type="button"
            className="button-primary"
            disabled={resend.isPending}
            onClick={() => resend.mutate()}
          >
            {resend.isPending ? '送信中…' : '確認メールを再送'}
          </button>
        )}
      </>
    )
  } else if (verification.isError) {
    body = (
      <>
        <p className={styles.alert} role="alert">
          {errorMessage(verification.error)}
        </p>
        <button
          type="button"
          className="button-primary"
          onClick={() => void verification.refetch()}
        >
          もう一度試す
        </button>
      </>
    )
  } else if (verification.isSuccess) {
    body = <p className={styles.message}>{successMessage(verification.data)}</p>
  } else {
    body = <p className={styles.message}>確認しています…</p>
  }

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <p className={styles.brand}>FIT LOG</p>
        <h1 className={styles.title}>メールアドレスの確認</h1>
        {body}
        {loading ? (
          <AuthLoading />
        ) : (
          <div className={styles.links}>
            {user ? <Link to="/">ホームへ</Link> : <Link to="/login">ログインへ</Link>}
          </div>
        )}
      </div>
    </main>
  )
}
