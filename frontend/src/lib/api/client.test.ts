import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import type { WorkoutInput } from '../validation/workout'
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

describe('記録（A-21・A-23）', () => {
  test('詳細は、ログインのトークンを付けて GET する', async () => {
    await loggedIn()
    const workout = { id: 12, body: '脚の日' }
    fetchMock.mockResolvedValueOnce(json(200, workout))

    await expect(client.fetchWorkout(12)).resolves.toEqual(workout)

    expect(call(1).url).toBe('/api/workouts/12')
    expect(call(1).init.method).toBe('GET')
    expect(authorization(1)).toBe('Bearer access-1')
  })

  test('削除は、本文のない 204 を受け取る', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(client.deleteWorkout(12)).resolves.toBeUndefined()

    expect(call(1).url).toBe('/api/workouts/12')
    expect(call(1).init.method).toBe('DELETE')
    expect(authorization(1)).toBe('Bearer access-1')
  })
})

describe('記録の作成・編集（A-20・A-22）', () => {
  const INPUT: WorkoutInput = {
    performedOn: '2026-11-22',
    body: '',
    durationMin: null,
    visibility: 'public',
    weightUnit: 'kg',
    distanceUnit: 'km',
    exercises: [{ exerciseId: 8, sets: [{ reps: 20 }] }],
  }

  async function dataPart(n: number): Promise<unknown> {
    const form = call(n).init.body as FormData
    const data = form.get('data') as Blob
    expect(data.type).toBe('application/json')
    return JSON.parse(await data.text())
  }

  test('作成は multipart で、記録の JSON を data のパートに入れる（Content-Type はブラウザに任せる）', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(201, { id: 5 }))

    await expect(client.createWorkout(INPUT)).resolves.toEqual({ id: 5 })

    expect(call(1).url).toBe('/api/workouts')
    expect(call(1).init.method).toBe('POST')
    expect(authorization(1)).toBe('Bearer access-1')
    expect((call(1).init.headers as Record<string, string>)['Content-Type']).toBeUndefined()
    expect(await dataPart(1)).toEqual(INPUT)
  })

  test('編集は PUT で、残す画像の ID を data に入れる', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, { id: 5 }))

    await client.updateWorkout(5, { ...INPUT, keepImageIds: [3, 1] })

    expect(call(1).url).toBe('/api/workouts/5')
    expect(call(1).init.method).toBe('PUT')
    expect(await dataPart(1)).toEqual({ ...INPUT, keepImageIds: [3, 1] })
  })

  test('画像は、選んだ順に images のパートに入れる', async () => {
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(json(201, { id: 5 }))
      .mockResolvedValueOnce(json(200, { id: 5 }))
    const first = new Blob(['a'], { type: 'image/jpeg' })
    const second = new Blob(['bb'], { type: 'image/jpeg' })

    await client.createWorkout(INPUT, [first, second])
    await client.updateWorkout(5, { ...INPUT, keepImageIds: [] }, [second])

    const created = (call(1).init.body as FormData).getAll('images') as File[]
    expect(created.map((image) => image.size)).toEqual([1, 2])
    expect(created.map((image) => image.name)).toEqual(['image-1.jpg', 'image-2.jpg'])
    const updated = (call(2).init.body as FormData).getAll('images') as File[]
    expect(updated.map((image) => image.size)).toEqual([2])
  })

  test('画像がなければ、images のパートを付けない', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(201, { id: 5 }))

    await client.createWorkout(INPUT)

    expect((call(1).init.body as FormData).has('images')).toBe(false)
  })

  test('multipart でも、トークンの期限切れなら再発行して送り直す', async () => {
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(error(401, 'UNAUTHENTICATED'))
      .mockResolvedValueOnce(json(200, auth('access-2')))
      .mockResolvedValueOnce(json(201, { id: 5 }))

    await client.createWorkout(INPUT)

    expect(authorization(3)).toBe('Bearer access-2')
    expect(await dataPart(3)).toEqual(INPUT)
  })
})

describe('種目（A-30・A-31）', () => {
  test('検索は、名前と部位を URL の条件にする（空なら付けない）', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, [])).mockResolvedValueOnce(json(200, []))

    await client.searchExercises({ q: 'ベンチ プレス', bodyPart: 'chest' })
    await client.searchExercises({ q: '', bodyPart: null })

    expect(call(1).url).toBe(
      '/api/exercises?q=%E3%83%99%E3%83%B3%E3%83%81+%E3%83%97%E3%83%AC%E3%82%B9&bodyPart=chest',
    )
    expect(call(2).url).toBe('/api/exercises')
    expect(authorization(1)).toBe('Bearer access-1')
  })

  test('最近使った種目', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, [{ id: 1 }]))

    await expect(client.fetchRecentExercises()).resolves.toEqual([{ id: 1 }])

    expect(call(1).url).toBe('/api/exercises/recent')
  })
})

describe('自分専用の種目（A-32〜A-34）', () => {
  test('追加は、名前・部位・記録方式を JSON で送る', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(201, { id: 200 }))

    await expect(
      client.createExercise({ name: '種目', bodyPart: 'legs', recordType: 'reps' }),
    ).resolves.toEqual({ id: 200 })

    expect(call(1).url).toBe('/api/exercises')
    expect(call(1).init.method).toBe('POST')
    expect(JSON.parse(call(1).init.body as string)).toEqual({
      name: '種目',
      bodyPart: 'legs',
      recordType: 'reps',
    })
    expect(authorization(1)).toBe('Bearer access-1')
  })

  test('変更は、名前と部位を PUT で送る', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, { id: 200 }))

    await client.updateExercise(200, { name: '新しい名前', bodyPart: 'back' })

    expect(call(1).url).toBe('/api/exercises/200')
    expect(call(1).init.method).toBe('PUT')
    expect(JSON.parse(call(1).init.body as string)).toEqual({
      name: '新しい名前',
      bodyPart: 'back',
    })
  })

  test('削除は、本文のない 204 を受け取る', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await expect(client.deleteExercise(200)).resolves.toBeUndefined()

    expect(call(1).url).toBe('/api/exercises/200')
    expect(call(1).init.method).toBe('DELETE')
  })
})

describe('通知（A-120〜A-122）', () => {
  test('一覧は、最初はカーソルなしで、続きはカーソルを URL の条件にする', async () => {
    await loggedIn()
    const page = { items: [], nextCursor: null, hasNext: false }
    fetchMock.mockResolvedValueOnce(json(200, page)).mockResolvedValueOnce(json(200, page))

    await expect(client.fetchNotifications()).resolves.toEqual(page)
    await client.fetchNotifications('abc_-1')

    expect(call(1).url).toBe('/api/notifications')
    expect(call(2).url).toBe('/api/notifications?cursor=abc_-1')
    expect(authorization(1)).toBe('Bearer access-1')
  })

  test('未読の数', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, { unreadCount: 3, followRequestCount: 0 }))

    await expect(client.fetchUnreadCount()).resolves.toEqual({
      unreadCount: 3,
      followRequestCount: 0,
    })

    expect(call(1).url).toBe('/api/notifications/unread-count')
  })

  test('既読は、表示した通知の ID を送る', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    await client.markNotificationsRead([3, 1])

    expect(call(1).url).toBe('/api/notifications/read')
    expect(call(1).init.method).toBe('POST')
    expect(JSON.parse(call(1).init.body as string)).toEqual({ ids: [3, 1] })
  })
})

describe('フォロー・プロフィール（A-70〜A-73・A-80）', () => {
  test('フォローは POST・解除は DELETE で、ユーザー名を URL に入れる（記号は符号化する）', async () => {
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(json(200, { followStatus: 'requested' }))
      .mockResolvedValueOnce(json(200, { followStatus: 'none' }))

    await expect(client.followUser('sato_1')).resolves.toEqual({ followStatus: 'requested' })
    await expect(client.unfollowUser('a/b')).resolves.toEqual({ followStatus: 'none' })

    expect(call(1).url).toBe('/api/users/sato_1/follow')
    expect(call(1).init.method).toBe('POST')
    expect(call(2).url).toBe('/api/users/a%2Fb/follow')
    expect(call(2).init.method).toBe('DELETE')
    expect(authorization(2)).toBe('Bearer access-1')
  })

  test('一覧は、最初はカーソルなしで、続きはカーソルを URL の条件にする', async () => {
    await loggedIn()
    const page = { items: [], nextCursor: null, hasNext: false }
    fetchMock.mockResolvedValueOnce(json(200, page)).mockResolvedValueOnce(json(200, page))

    await expect(client.fetchFollowing('sato')).resolves.toEqual(page)
    await client.fetchFollowers('sato', 'abc_-1')

    expect(call(1).url).toBe('/api/users/sato/following')
    expect(call(2).url).toBe('/api/users/sato/followers?cursor=abc_-1')
  })

  test('プロフィール', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, { username: 'sato' }))

    await expect(client.fetchProfile('sato')).resolves.toEqual({ username: 'sato' })

    expect(call(1).url).toBe('/api/users/sato')
  })
})

describe('ナイストレーニング（A-63・A-64）', () => {
  test('付けるのは POST、取り消すのは DELETE で、数と付けたかが返る', async () => {
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(json(200, { likeCount: 3, likedByMe: true }))
      .mockResolvedValueOnce(json(200, { likeCount: 2, likedByMe: false }))

    await expect(client.likeWorkout(12)).resolves.toEqual({ likeCount: 3, likedByMe: true })
    await expect(client.unlikeWorkout(12)).resolves.toEqual({ likeCount: 2, likedByMe: false })

    expect(call(1).url).toBe('/api/workouts/12/likes')
    expect(call(1).init.method).toBe('POST')
    expect(call(2).url).toBe('/api/workouts/12/likes')
    expect(call(2).init.method).toBe('DELETE')
    expect(authorization(1)).toBe('Bearer access-1')
  })
})

describe('コメント・返信（A-60〜A-62・A-65）', () => {
  const page = { items: [], nextCursor: null, hasNext: false }

  test('一覧と返信の続き。続きはカーソルを付ける', async () => {
    await loggedIn()
    fetchMock.mockImplementation(async () => json(200, page))

    await expect(client.fetchComments(12)).resolves.toEqual(page)
    await client.fetchComments(12, 'abc_-1')
    await client.fetchReplies(501)
    await client.fetchReplies(501, 'abc_-1')

    expect(call(1).url).toBe('/api/workouts/12/comments')
    expect(call(2).url).toBe('/api/workouts/12/comments?cursor=abc_-1')
    expect(call(3).url).toBe('/api/comments/501/replies')
    expect(call(4).url).toBe('/api/comments/501/replies?cursor=abc_-1')
  })

  test('投稿は本文と、返信なら返信するコメントを JSON で送る', async () => {
    await loggedIn()
    const posted = { comment: { id: 1 }, commentCount: 3 }
    fetchMock.mockImplementation(async () => json(201, posted))

    await expect(client.postComment(12, 'ナイス')).resolves.toEqual(posted)
    await client.postComment(12, '@sato ありがとう', 501)

    expect(call(1).url).toBe('/api/workouts/12/comments')
    expect(call(1).init.method).toBe('POST')
    expect(JSON.parse(call(1).init.body as string)).toEqual({ body: 'ナイス' })
    expect(JSON.parse(call(2).init.body as string)).toEqual({
      body: '@sato ありがとう',
      replyToCommentId: 501,
    })
  })

  test('削除は DELETE で、削除後のコメント数が返る', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, { commentCount: 2 }))

    await expect(client.deleteComment(501)).resolves.toEqual({ commentCount: 2 })

    expect(call(1).url).toBe('/api/comments/501')
    expect(call(1).init.method).toBe('DELETE')
  })
})

describe('タイムライン・記録の一覧（A-50〜A-53・A-81）', () => {
  const page = { items: [], nextCursor: null, hasNext: false }

  test('フォロー中と全体のタイムライン。続きはカーソルを付ける', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, page)).mockResolvedValueOnce(json(200, page))

    await expect(client.fetchTimeline('following')).resolves.toEqual(page)
    await client.fetchTimeline('all', 'abc_-1')

    expect(call(1).url).toBe('/api/timeline')
    expect(call(2).url).toBe('/api/timeline/all?cursor=abc_-1')
    expect(authorization(1)).toBe('Bearer access-1')
  })

  test('新しい投稿の件数', async () => {
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(json(200, { count: 3 }))
      .mockResolvedValueOnce(json(200, { count: 0 }))

    await expect(client.fetchNewPostCount('following', 123)).resolves.toEqual({ count: 3 })
    await client.fetchNewPostCount('all', 0)

    expect(call(1).url).toBe('/api/timeline/new-count?since=123')
    expect(call(2).url).toBe('/api/timeline/all/new-count?since=0')
  })

  test('そのユーザーの記録一覧', async () => {
    await loggedIn()
    fetchMock.mockResolvedValueOnce(json(200, page)).mockResolvedValueOnce(json(200, page))

    await expect(client.fetchUserWorkouts('sato')).resolves.toEqual(page)
    await client.fetchUserWorkouts('sato', 'abc_-1')

    expect(call(1).url).toBe('/api/users/sato/workouts')
    expect(call(2).url).toBe('/api/users/sato/workouts?cursor=abc_-1')
  })
})

describe('プロフィールの編集（A-82）', () => {
  test('表示名と自己紹介を data のパートに、アイコンを icon のパートに入れて PUT で送る', async () => {
    await loggedIn()
    fetchMock
      .mockResolvedValueOnce(json(200, { username: 'yamada' }))
      .mockResolvedValueOnce(json(200, { username: 'yamada' }))
    const icon = new Blob(['i'], { type: 'image/jpeg' })

    await expect(client.updateProfile({ displayName: '山田', bio: '' }, icon)).resolves.toEqual({
      username: 'yamada',
    })
    await client.updateProfile({ displayName: '山田', bio: 'こんにちは' })

    expect(call(1).url).toBe('/api/users/me')
    expect(call(1).init.method).toBe('PUT')
    const form = call(1).init.body as FormData
    expect(JSON.parse(await (form.get('data') as Blob).text())).toEqual({
      displayName: '山田',
      bio: '',
    })
    expect(form.get('icon')).toBeInstanceOf(Blob)
    expect((call(2).init.body as FormData).has('icon')).toBe(false)
  })
})
