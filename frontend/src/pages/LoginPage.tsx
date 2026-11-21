import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { errorMessage } from '../lib/api/client'
import { useAuth } from '../lib/auth/authContext'
import { type FormErrors, type LoginForm, validateLogin } from '../lib/validation/auth'
import { FormField } from '../components/FormField'
import styles from './AuthPage.module.css'

/**
 * S-01 ログイン（docs/screens.md）。ログインできたら、画面の守り（PublicOnly）がホームか、もとの画面へ移動する。
 * 「パスワードを忘れた方」（S-03）は、パスワードのリセットを作る段階 5 で足す
 */
export function LoginPage() {
  const { login } = useAuth()
  const [form, setForm] = useState<LoginForm>({ email: '', password: '' })
  const [errors, setErrors] = useState<FormErrors<LoginForm>>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const found = validateLogin(form)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setSubmitting(true)
    setFailure(null)
    try {
      await login({ email: form.email.trim(), password: form.password })
    } catch (err) {
      // 入力した内容は消さない（docs/screens.md「確認ダイアログ・お知らせ」）
      setFailure(errorMessage(err))
      setSubmitting(false)
    }
  }

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <p className={styles.brand}>FIT LOG</p>
        <h1 className={styles.title}>ログイン</h1>
        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          {failure && (
            <p className={styles.alert} role="alert">
              {failure}
            </p>
          )}
          <FormField
            label="メールアドレス"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={(e) => setForm({ ...form, email: e.target.value })}
            error={errors.email}
          />
          <FormField
            label="パスワード"
            type="password"
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => setForm({ ...form, password: e.target.value })}
            error={errors.password}
          />
          <button type="submit" className="button-primary" disabled={submitting}>
            {submitting ? 'ログイン中…' : 'ログイン'}
          </button>
        </form>
        <div className={styles.links}>
          <span>アカウントをお持ちでない方は</span>
          <Link to="/signup">新規登録はこちら</Link>
        </div>
      </div>
    </main>
  )
}
