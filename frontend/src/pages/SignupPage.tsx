import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { FormField } from '../components/FormField'
import { ApiError, errorMessage } from '../lib/api/client'
import { useAuth } from '../lib/auth/authContext'
import { useToast } from '../lib/toast'
import { type FormErrors, type SignupForm, validateSignup } from '../lib/validation/auth'
import styles from './AuthPage.module.css'

const EMPTY: SignupForm = {
  username: '',
  displayName: '',
  email: '',
  password: '',
  passwordConfirm: '',
}

const FIELDS = new Set<keyof SignupForm>(Object.keys(EMPTY) as (keyof SignupForm)[])

/** 登録のあとに出す案内（docs/screens.md S-02） */
export const SIGNUP_NOTICE =
  '確認のメールを送りました。メールのリンクを開いて、メールアドレスの確認を済ませてください'

/** S-02 新規登録（docs/screens.md）。登録したらログイン中になり、画面の守り（PublicOnly）がホームへ移す */
export function SignupPage() {
  const { signup } = useAuth()
  const toast = useToast()
  const [form, setForm] = useState<SignupForm>(EMPTY)
  const [errors, setErrors] = useState<FormErrors<SignupForm>>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function field(name: keyof SignupForm) {
    return {
      value: form[name],
      onChange: (e: { target: { value: string } }) => setForm({ ...form, [name]: e.target.value }),
      error: errors[name],
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    const found = validateSignup(form)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setSubmitting(true)
    setFailure(null)
    try {
      await signup({
        username: form.username,
        displayName: form.displayName,
        email: form.email.trim(),
        password: form.password,
      })
      toast(SIGNUP_NOTICE)
    } catch (err) {
      showFailure(err)
      setSubmitting(false)
    }
  }

  /** 項目に結び付くエラー（使われているユーザー名など）はその項目に、それ以外は上にまとめて出す。入力は残す */
  function showFailure(err: unknown) {
    const fieldErrors = err instanceof ApiError ? err.fieldErrors : []
    const known = fieldErrors.filter((e) => FIELDS.has(e.field as keyof SignupForm))
    if (known.length === 0) {
      setFailure(errorMessage(err))
      return
    }
    setErrors(Object.fromEntries(known.map((e) => [e.field, e.message])))
  }

  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <p className={styles.brand}>FIT LOG</p>
        <h1 className={styles.title}>新規登録</h1>
        <form className={styles.form} onSubmit={handleSubmit} noValidate>
          {failure && (
            <p className={styles.alert} role="alert">
              {failure}
            </p>
          )}
          <FormField label="ユーザー名" autoComplete="username" {...field('username')} />
          <FormField label="表示名" autoComplete="nickname" {...field('displayName')} />
          <FormField label="メールアドレス" type="email" autoComplete="email" {...field('email')} />
          <FormField
            label="パスワード"
            type="password"
            autoComplete="new-password"
            {...field('password')}
          />
          <FormField
            label="パスワード（確認）"
            type="password"
            autoComplete="new-password"
            {...field('passwordConfirm')}
          />
          <button type="submit" className="button-primary" disabled={submitting}>
            {submitting ? '登録中…' : '登録する'}
          </button>
        </form>
        <div className={styles.links}>
          <span>アカウントをお持ちの方は</span>
          <Link to="/login">ログインはこちら</Link>
        </div>
      </div>
    </main>
  )
}
