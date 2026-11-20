import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import type { AuthResponse } from './types'

// client.ts はアクセストークン・再発行中の Promise をモジュールの変数に持つので、テストごとに読み込み直す
type Client = typeof import('./client')
let client: Client

const fetchMock = vi.fn<typeof fetch>()

beforeEach(async () => {
  vi.resetModules()
  vi.stubGlobal('fetch', fetchMock)
  fetchMock.mockReset()
  client = await import('./client')
})

afterEach(() => {
  vi.unstubAllGlobals()
})

const ME = {
  username: 'yamada',
  displayName: '山田',
  iconUrl: null,
  bio: null,
  email: 'yamada@example.com',
  emailVerified: false,
  isPrivate: false,
  weightUnit: 'kg',
  distanceUnit: 'km',
} as const

function auth(token: string): AuthResponse {
  return { accessToken: token, user: { ...ME } }
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function error(status: number, code: string, message = 'エラー', errors: unknown[] = []): Response {
  return json(status, { status, code, message, errors })
}

/** n 回目の呼び出しの URL と設定 */
function call(n: number): { url: string; init: RequestInit } {
  const [url, init] = fetchMock.mock.calls[n]
  return { url: String(url), init: init ?? {} }
}

function authorization(n: number): string | undefined {
  return (call(n).init.headers as Record<string, string>).Authorization
}

async function loggedIn(token = 'access-1'): Promise<void> {
  fetchMock.mockResolvedValueOnce(json(200, auth(token)))
  await client.login({ email: 'yamada@example.com', password: 'password123' })
}

describe('トークンの受け渡し', () => {
  test('ログインで受け取ったアクセストークンを、ログインが必要な API に付ける', async () => {
    await loggedIn('access-1')
    fetchMock.mockResolvedValueOnce(json(200, ME))

    await expect(client.fetchMe()).resolves.toEqual(ME)

    expect(call(0).url).toBe('/api/auth/login')
    expect(call(0).init.method).toBe('POST')
    expect(JSON.parse(String(call(0).init.body))).toEqual({
      email: 'yamada@example.com',
      password: 'password123',
    })
    expect(call(1).url).toBe('/api/auth/me')
    expect(authorization(1)).toBe('Bearer access-1')
  })

  test('新規登録で受け取ったアクセストークンも使う', async () => {
    fetchMock.mockResolvedValueOnce(json(201, auth('access-new')))
    await client.signup({
      username: 'yamada',
      displayName: '山田',
      email: 'yamada@example.com',
      password: 'password123',
    })
    fetchMock.mockResolvedValueOnce(json(200, ME))

    await client.fetchMe()

    expect(call(0).url).toBe('/api/auth/signup')
    expect(authorization(1)).toBe('Bearer access-new')
  })

  test('ログアウトしたら、アクセストークンを付けない', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await client.logout()
    // 付けずに送ると 401 → 再発行も 401
    fetchMock
      .mockResolvedValueOnce(error(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(error(401, 'SESSION_EXPIRED'))

    await expect(client.fetchMe()).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })

    expect(call(1).url).toBe('/api/auth/logout')
    expect(authorization(2)).toBeUndefined()
  })

  test('ログアウトの通信に失敗しても、アクセストークンは捨てる', async () => {
    await loggedIn()
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    await expect(client.logout()).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    fetchMock
      .mockResolvedValueOnce(error(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(error(401, 'SESSION_EXPIRED'))

    await expect(client.fetchMe()).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })

    expect(authorization(2)).toBeUndefined()
  })
})

describe('期限切れのときの再発行とやり直し（状態遷移）', () => {
  test('401 UNAUTHENTICATED なら、再発行して新しいトークンで1回だけやり直す', async () => {
    await loggedIn('access-1')
    fetchMock
      .mockResolvedValueOnce(error(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(json(200, auth('access-2')))
      .mockResolvedValueOnce(json(200, ME))

    await expect(client.fetchMe()).resolves.toEqual(ME)

    expect(call(2).url).toBe('/api/auth/refresh')
    expect(call(2).init.method).toBe('POST')
    expect(authorization(3)).toBe('Bearer access-2')
  })

  test('再発行もできなければ（Cookie の期限切れ）、ログアウトの処理を呼んで再発行のエラーを返す', async () => {
    const onUnauthorized = vi.fn<() => void>()
    client.setUnauthorizedHandler(onUnauthorized)
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(error(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(error(401, 'SESSION_EXPIRED'))

    await expect(client.fetchMe()).rejects.toMatchObject({ code: 'SESSION_EXPIRED', status: 401 })

    expect(onUnauthorized).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  test('やり直しでも 401 なら、それ以上はやり直さずにログアウトの処理を呼ぶ', async () => {
    const onUnauthorized = vi.fn<() => void>()
    client.setUnauthorizedHandler(onUnauthorized)
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(error(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(json(200, auth('access-2')))
      .mockResolvedValueOnce(error(401, 'UNAUTHENTICATED'))

    await expect(client.fetchMe()).rejects.toMatchObject({ code: 'UNAUTHENTICATED' })

    expect(onUnauthorized).toHaveBeenCalledOnce()
    expect(fetchMock).toHaveBeenCalledTimes(4)
  })

  test('やり直しが 401 以外のエラーなら、ログアウトの処理は呼ばない', async () => {
    const onUnauthorized = vi.fn<() => void>()
    client.setUnauthorizedHandler(onUnauthorized)
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(error(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(json(200, auth('access-2')))
      .mockResolvedValueOnce(error(409, 'ALREADY_VERIFIED'))

    await expect(client.resendVerificationEmail()).rejects.toMatchObject({
      code: 'ALREADY_VERIFIED',
    })

    expect(onUnauthorized).not.toHaveBeenCalled()
  })

  test('同時に2つの API が 401 になっても、再発行は1回にまとめる（リフレッシュトークンは一度使うと交換されるため）', async () => {
    await loggedIn('access-1')
    let resolveRefresh!: (res: Response) => void
    fetchMock.mockImplementation((input) => {
      const url = String(input)
      if (url === '/api/auth/refresh') {
        return new Promise((resolve) => {
          resolveRefresh = resolve
        })
      }
      return Promise.resolve(error(401, 'UNAUTHENTICATED'))
    })

    const first = client.fetchMe()
    const second = client.fetchMe()
    await vi.waitFor(() => expect(resolveRefresh).toBeDefined())
    fetchMock.mockImplementation(() => Promise.resolve(json(200, ME)))
    resolveRefresh(json(200, auth('access-2')))

    await expect(Promise.all([first, second])).resolves.toEqual([ME, ME])
    const refreshCalls = fetchMock.mock.calls.filter(([url]) => url === '/api/auth/refresh')
    expect(refreshCalls).toHaveLength(1)
  })

  test('ログインが要らない API の 401（パスワードが違う）では、再発行しない', async () => {
    fetchMock.mockResolvedValueOnce(
      error(401, 'INVALID_CREDENTIALS', 'メールアドレスまたはパスワードが正しくありません'),
    )

    await expect(client.login({ email: 'a@example.com', password: 'x' })).rejects.toMatchObject({
      status: 401,
      code: 'INVALID_CREDENTIALS',
      message: 'メールアドレスまたはパスワードが正しくありません',
    })

    expect(fetchMock).toHaveBeenCalledOnce()
  })

  test('401 でも UNAUTHENTICATED 以外なら、再発行しない', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(error(401, 'SESSION_EXPIRED'))

    await expect(client.fetchMe()).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  test('再発行（アプリを開いたときの復元）で受け取ったトークンを使う', async () => {
    fetchMock.mockResolvedValueOnce(json(200, auth('access-restored')))
    await expect(client.refresh()).resolves.toEqual(auth('access-restored'))
    fetchMock.mockResolvedValueOnce(json(200, ME))

    await client.fetchMe()

    expect(authorization(1)).toBe('Bearer access-restored')
  })
})

describe('エラーの変換', () => {
  test('項目ごとのエラーを持つ', async () => {
    const errors = [{ field: 'username', message: 'このユーザー名はすでに使われています' }]
    fetchMock.mockResolvedValueOnce(
      error(409, 'ALREADY_REGISTERED', 'すでに登録されています', errors),
    )

    await expect(
      client.signup({ username: 'a', displayName: 'a', email: 'a', password: 'a' }),
    ).rejects.toMatchObject({ code: 'ALREADY_REGISTERED', fieldErrors: errors })
  })

  test('通信そのものに失敗したら、状態 0 の NETWORK_ERROR', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))

    await expect(client.login({ email: 'a', password: 'b' })).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
      message: client.NETWORK_ERROR_MESSAGE,
    })
  })

  test('API の形でないエラー（途中のプロキシのエラーページ）は UNKNOWN', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>Bad Gateway</html>', { status: 502 }))

    await expect(client.login({ email: 'a', password: 'b' })).rejects.toMatchObject({
      status: 502,
      code: 'UNKNOWN',
      message: client.NETWORK_ERROR_MESSAGE,
    })
  })

  test('errorMessage は、API のエラーなら文言を、それ以外なら通信エラーの文言を返す', () => {
    expect(client.errorMessage(new client.ApiError(429, 'TOO_MANY_EMAILS', '送れません'))).toBe(
      '送れません',
    )
    expect(client.errorMessage(new Error('想定外'))).toBe(client.NETWORK_ERROR_MESSAGE)
  })
})

describe('メールアドレスの確認（A-06・A-07）', () => {
  test('確認はトークンを本文で送り、ログインしていなくても呼べる', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { result: 'verified', email: 'yamada@example.com' }))

    await expect(client.verifyEmail('token-1')).resolves.toEqual({
      result: 'verified',
      email: 'yamada@example.com',
    })

    expect(call(0).url).toBe('/api/auth/email-verification')
    expect(call(0).init.method).toBe('POST')
    expect(JSON.parse(String(call(0).init.body))).toEqual({ token: 'token-1' })
  })

  test('再送は本文のない 202 を受け取る', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 202 }))

    await expect(client.resendVerificationEmail()).resolves.toBeUndefined()

    expect(call(1).url).toBe('/api/account/email-verification')
    expect(call(1).init.method).toBe('POST')
    expect(authorization(1)).toBe('Bearer access-1')
  })
})
