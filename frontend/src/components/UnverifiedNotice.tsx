import { useState } from 'react'
import { useAuth } from '../lib/auth/authContext'
import { useResendVerification } from '../lib/auth/useResendVerification'
import { dismissNotice, isNoticeDismissed } from '../lib/unverifiedNotice'
import styles from './UnverifiedNotice.module.css'

/** ブラウザの保存先。使えない設定（保存の禁止など）では、読むだけで例外になることがある */
function browserStorage(): Storage | null {
  try {
    return window.localStorage
  } catch {
    return null
  }
}

/**
 * メールアドレスが未確認のときの案内（docs/screens.md「ナビゲーション」、F-05）。
 * 閉じるボタンで、その日（日本時間）は出さない
 */
export function UnverifiedNotice() {
  const { user } = useAuth()
  const resend = useResendVerification()
  const [dismissed, setDismissed] = useState(() => {
    const storage = browserStorage()
    return storage !== null && isNoticeDismissed(storage, new Date())
  })

  if (!user || user.emailVerified || dismissed) return null

  function close() {
    const storage = browserStorage()
    if (storage) dismissNotice(storage, new Date())
    setDismissed(true)
  }

  return (
    <aside className={styles.notice} aria-label="メールアドレスの確認">
      <p className={styles.text}>
        メールアドレスの確認が済んでいません。届いたメールのリンクを開いてください
      </p>
      <div className={styles.actions}>
        <button
          type="button"
          className="button-secondary"
          disabled={resend.isPending}
          onClick={() => resend.mutate()}
        >
          {resend.isPending ? '送信中…' : '確認メールを再送'}
        </button>
        <button type="button" className={styles.close} aria-label="案内を閉じる" onClick={close}>
          ×
        </button>
      </div>
    </aside>
  )
}
