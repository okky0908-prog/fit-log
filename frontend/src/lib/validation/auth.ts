// 認証の画面の入力チェック（docs/screens.md S-01・S-02、docs/test-plan.md 5.1）。
// 画面のチェックは使いやすさのためで、サーバーでも同じチェックをする。文言はサーバー（SignupRequest・LoginRequest）と同じにする

export interface SignupForm {
  username: string
  displayName: string
  email: string
  password: string
  /** パスワード（確認）。画面だけでチェックし、サーバーには送らない */
  passwordConfirm: string
}

export interface LoginForm {
  email: string
  password: string
}

/** 項目ごとのエラーの文言。エラーのない項目は入らない */
export type FormErrors<T> = Partial<Record<keyof T, string>>

const USERNAME = /^[A-Za-z0-9_]{4,15}$/
const EMAIL = /^.+@.+\..+$/
const PASSWORD_MAX_BYTES = 72

/** 文字数。前後の空白を除き、コードポイントで数える（絵文字も1文字。サーバーの TextLength と同じ） */
function textLength(value: string): number {
  return [...value.trim()].length
}

export function validateSignup(form: SignupForm): FormErrors<SignupForm> {
  const errors: FormErrors<SignupForm> = {}
  if (!USERNAME.test(form.username)) {
    errors.username = 'ユーザー名は半角英数字と_で4〜15文字で入力してください'
  }
  const displayNameLength = textLength(form.displayName)
  if (displayNameLength < 1 || displayNameLength > 50) {
    errors.displayName = '表示名は1〜50文字で入力してください'
  }
  if (!EMAIL.test(form.email.trim())) {
    errors.email = 'メールアドレスの形式が正しくありません'
  }
  const passwordError = validatePassword(form.password)
  if (passwordError) {
    errors.password = passwordError
  }
  if (form.passwordConfirm !== form.password) {
    errors.passwordConfirm = 'パスワードが一致しません'
  }
  return errors
}

/** パスワードは8〜72文字で、72バイト（BCrypt が扱える長さ）まで。文字数は Java と同じく UTF-16 の長さで数える */
function validatePassword(password: string): string | undefined {
  if (password.length < 8) {
    return 'パスワードは8文字以上で入力してください'
  }
  if (password.length > 72) {
    return 'パスワードは72文字以内で入力してください'
  }
  if (new TextEncoder().encode(password).length > PASSWORD_MAX_BYTES) {
    return 'パスワードが長すぎます（全角文字は1文字を3バイトとして、72バイトまで）'
  }
  return undefined
}

export function validateLogin(form: LoginForm): FormErrors<LoginForm> {
  const errors: FormErrors<LoginForm> = {}
  if (form.email.trim() === '') {
    errors.email = 'メールアドレスを入力してください'
  }
  if (form.password === '') {
    errors.password = 'パスワードを入力してください'
  }
  return errors
}
