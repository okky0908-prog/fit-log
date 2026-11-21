import type {
  ApiErrorBody,
  ApiErrorCode,
  AuthResponse,
  FieldErrorBody,
  LoginInput,
  Me,
  SignupInput,
  VerifyEmailResult,
  Workout,
} from './types'

/*
 * API の呼び出し（docs/api.md「共通のルール」。前回の SNS アプリと同じ方式）
 * - アクセストークン（15分）：このファイルの変数（メモリ）にだけ持つ。localStorage には入れない（XSS で盗まれないように）
 * - リフレッシュトークン（14日）：サーバーが HttpOnly の Cookie で渡す。JavaScript からは読めず、ブラウザが /api/auth の下にだけ送る
 * - ログインが必要な API が 401（UNAUTHENTICATED）なら、再発行して1回だけやり直す
 */

let accessToken: string | null = null

// ===== エラー =====

/**
 * エラーの種類。API の code に、画面だけで使うものを足す
 * - NETWORK_ERROR：通信そのものに失敗した（オフライン・サーバーが止まっている など）
 * - UNKNOWN：API の形でないエラーが返った（途中のプロキシのエラーページ など）
 */
export type ErrorCode = ApiErrorCode | 'NETWORK_ERROR' | 'UNKNOWN'

/** 通信に失敗したときの文言（docs/screens.md「確認ダイアログ・お知らせ」） */
export const NETWORK_ERROR_MESSAGE = '通信に失敗しました。もう一度お試しください'

/** API がエラーを返した・通信に失敗したときに投げる。画面は status ではなく code で見分ける */
export class ApiError extends Error {
  /** HTTP のステータス。通信に失敗したときは 0 */
  readonly status: number
  readonly code: ErrorCode
  readonly fieldErrors: FieldErrorBody[]

  constructor(
    status: number,
    code: ErrorCode,
    message: string,
    fieldErrors: FieldErrorBody[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }
}

/** 利用者に見せる文言。API のエラーならその文言、それ以外（想定外の例外）は通信エラーの文言 */
export function errorMessage(err: unknown): string {
  return err instanceof ApiError ? err.message : NETWORK_ERROR_MESSAGE
}

function isUnauthenticated(err: unknown): boolean {
  return err instanceof ApiError && err.code === 'UNAUTHENTICATED'
}

// 再発行もできずに 401 になったときの処理（AuthProvider がログアウトの状態にする処理を登録する）
let unauthorizedHandler: (() => void) | null = null

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler
}

function signedOut(): void {
  accessToken = null
  unauthorizedHandler?.()
}

// ===== 共通の呼び出し =====

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  /** JSON にして送る値 */
  body?: unknown
  /** true ならアクセストークンを付け、401 なら再発行して1回だけやり直す */
  auth?: boolean
}

/** 1回だけ送る（Cookie は同じオリジンなので、ブラウザが付ける） */
async function send<T>(
  path: string,
  { method = 'GET', body, auth = false }: RequestOptions,
): Promise<T> {
  const headers: Record<string, string> = {}
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (auth && accessToken) {
    headers.Authorization = `Bearer ${accessToken}`
  }

  let res: Response
  try {
    res = await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', NETWORK_ERROR_MESSAGE)
  }

  if (!res.ok) {
    const errorBody = (await res.json().catch(() => null)) as Partial<ApiErrorBody> | null
    if (!errorBody?.code || !errorBody.message) {
      throw new ApiError(res.status, 'UNKNOWN', NETWORK_ERROR_MESSAGE)
    }
    throw new ApiError(res.status, errorBody.code, errorBody.message, errorBody.errors ?? [])
  }
  // 本文のない応答（204 のログアウト・202 の再送）
  const text = await res.text()
  return (text === '' ? undefined : JSON.parse(text)) as T
}

async function apiFetch<T>(path: string, options: RequestOptions = {}): Promise<T> {
  try {
    return await send<T>(path, options)
  } catch (err) {
    if (!options.auth || !isUnauthenticated(err)) {
      throw err
    }
  }
  // アクセストークンの期限切れ。再発行して1回だけやり直す
  try {
    await refresh()
  } catch (refreshError) {
    signedOut()
    throw refreshError
  }
  try {
    return await send<T>(path, options)
  } catch (err) {
    if (isUnauthenticated(err)) {
      signedOut()
    }
    throw err
  }
}

// ===== 認証（docs/api.md の A-01〜A-05） =====

function keepAccessToken(res: AuthResponse): AuthResponse {
  accessToken = res.accessToken
  return res
}

/** A-01 新規登録。登録のあとはそのままログイン状態になる */
export async function signup(input: SignupInput): Promise<AuthResponse> {
  return keepAccessToken(await apiFetch('/api/auth/signup', { method: 'POST', body: input }))
}

/** A-02 ログイン */
export async function login(input: LoginInput): Promise<AuthResponse> {
  return keepAccessToken(await apiFetch('/api/auth/login', { method: 'POST', body: input }))
}

let refreshing: Promise<AuthResponse> | null = null

/**
 * A-04 リフレッシュトークン（Cookie）でアクセストークンを再発行する（アプリを開いたときの復元にも使う）。
 * リフレッシュトークンは一度使うと新しいものに交換されるので、同時に呼ばれても再発行は1回にまとめる
 */
export function refresh(): Promise<AuthResponse> {
  refreshing ??= send<AuthResponse>('/api/auth/refresh', { method: 'POST' })
    .then(keepAccessToken)
    .finally(() => {
      refreshing = null
    })
  return refreshing
}

/** A-05 ログアウト。サーバーでリフレッシュトークンを無効にし、手元のアクセストークンも捨てる（通信に失敗しても捨てる） */
export async function logout(): Promise<void> {
  try {
    await send<void>('/api/auth/logout', { method: 'POST' })
  } finally {
    accessToken = null
  }
}

/** A-03 ログイン中のユーザー */
export function fetchMe(): Promise<Me> {
  return apiFetch('/api/auth/me', { auth: true })
}

// ===== メールアドレスの確認（A-06・A-07） =====

/** A-06 メールアドレスの確認。ログインしていなくても呼べる。トークンは URL ではなく本文で送る（サーバーのログに残さないため） */
export function verifyEmail(token: string): Promise<VerifyEmailResult> {
  return apiFetch('/api/auth/email-verification', { method: 'POST', body: { token } })
}

/** A-07 確認メールの再送 */
export function resendVerificationEmail(): Promise<void> {
  return apiFetch('/api/account/email-verification', { method: 'POST', auth: true })
}

// ===== 記録（A-21・A-23） =====

/** A-21 記録の詳細。見てはいけない・ない記録は 404（WORKOUT_NOT_FOUND） */
export function fetchWorkout(id: number): Promise<Workout> {
  return apiFetch(`/api/workouts/${id}`, { auth: true })
}

/** A-23 記録の削除。他人の記録は 403（FORBIDDEN） */
export function deleteWorkout(id: number): Promise<void> {
  return apiFetch(`/api/workouts/${id}`, { method: 'DELETE', auth: true })
}
