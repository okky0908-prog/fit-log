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
  WorkoutCard,
  LikeState,
  Comment,
  PostedComment,
  Reply,
  Exercise,
  NotificationItem,
  Page,
  UnreadCount,
  FollowStatus,
  Profile,
  UserListItem,
  WeeklyStats,
  MonthlyStats,
  CalendarDates,
  RunningStats,
  UsedExercise,
  TrendRange,
  ExerciseTrend,
} from './types'
import type { WorkoutInput } from '../validation/workout'

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
  /** multipart/form-data で送る値（画像を送る API）。Content-Type は、区切りの文字列を付けてブラウザが決める */
  form?: FormData
  /** true ならアクセストークンを付け、401 なら再発行して1回だけやり直す */
  auth?: boolean
}

/** 1回だけ送る（Cookie は同じオリジンなので、ブラウザが付ける） */
async function send<T>(
  path: string,
  { method = 'GET', body, form, auth = false }: RequestOptions,
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
      body: form ?? (body === undefined ? undefined : JSON.stringify(body)),
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

// ===== ナイストレーニング（A-63・A-64） =====

/** A-63 ナイストレーニングを付ける。すでに付いていても今の状態が返る */
export function likeWorkout(workoutId: number): Promise<LikeState> {
  return apiFetch(`/api/workouts/${workoutId}/likes`, { method: 'POST', auth: true })
}

/** A-64 ナイストレーニングを取り消す。付いていなくても今の状態が返る */
export function unlikeWorkout(workoutId: number): Promise<LikeState> {
  return apiFetch(`/api/workouts/${workoutId}/likes`, { method: 'DELETE', auth: true })
}

// ===== コメント・返信（A-60〜A-62・A-65） =====

/** A-60 コメント一覧（返信でないコメントを古い順に20件。返信は各コメントに3件まで） */
export function fetchComments(workoutId: number, cursor?: string): Promise<Page<Comment>> {
  return apiFetch(`/api/workouts/${workoutId}/comments${cursorQuery(cursor)}`, { auth: true })
}

/** A-61 コメント・返信の投稿。replyToCommentId は返信するコメント・返信 */
export function postComment(
  workoutId: number,
  body: string,
  replyToCommentId?: number,
): Promise<PostedComment> {
  return apiFetch(`/api/workouts/${workoutId}/comments`, {
    method: 'POST',
    body: replyToCommentId === undefined ? { body } : { body, replyToCommentId },
    auth: true,
  })
}

/** A-62 コメントの削除。削除後のコメント数が返る */
export function deleteComment(commentId: number): Promise<{ commentCount: number }> {
  return apiFetch(`/api/comments/${commentId}`, { method: 'DELETE', auth: true })
}

/** A-65 返信の続き（古い順に20件）。最初は A-60 の repliesNextCursor を渡す */
export function fetchReplies(commentId: number, cursor?: string): Promise<Page<Reply>> {
  return apiFetch(`/api/comments/${commentId}/replies${cursorQuery(cursor)}`, { auth: true })
}

// ===== 記録の作成・編集（A-20・A-22）・種目（A-30・A-31） =====

/**
 * 記録の JSON を data のパートに、画像を images のパートに選んだ順で入れる（docs/api.md A-20）。
 * 画像は縮小した JPEG（lib/image/shrink.ts）。ファイル名はサーバーが使わないので、順番の番号にする
 */
function workoutForm(input: object, images: Blob[]): FormData {
  const form = new FormData()
  form.append('data', new Blob([JSON.stringify(input)], { type: 'application/json' }))
  images.forEach((image, index) => form.append('images', image, `image-${index + 1}.jpg`))
  return form
}

/** A-20 記録の作成 */
export function createWorkout(input: WorkoutInput, images: Blob[] = []): Promise<Workout> {
  return apiFetch('/api/workouts', { method: 'POST', form: workoutForm(input, images), auth: true })
}

/** A-22 記録の編集。keepImageIds は残す画像の ID（並び順のとおり）、images は残す画像のあとに並べる新しい画像 */
export function updateWorkout(
  id: number,
  input: WorkoutInput & { keepImageIds: number[] },
  images: Blob[] = [],
): Promise<Workout> {
  return apiFetch(`/api/workouts/${id}`, {
    method: 'PUT',
    form: workoutForm(input, images),
    auth: true,
  })
}

/** A-30 種目の検索。名前（部分一致）・部位が空なら、その条件では絞らない */
export function searchExercises({
  q,
  bodyPart,
}: {
  q: string
  bodyPart: string | null
}): Promise<Exercise[]> {
  const params = new URLSearchParams()
  if (q !== '') params.set('q', q)
  if (bodyPart) params.set('bodyPart', bodyPart)
  const query = params.toString()
  return apiFetch(`/api/exercises${query === '' ? '' : `?${query}`}`, { auth: true })
}

/** A-31 自分が最近使った種目（10件まで） */
export function fetchRecentExercises(): Promise<Exercise[]> {
  return apiFetch('/api/exercises/recent', { auth: true })
}

// ===== 自分専用の種目（A-32〜A-34） =====

/** A-32 自分専用の種目の追加。同じ名前は 409（EXERCISE_NAME_TAKEN）、100件あれば 409（EXERCISE_LIMIT_EXCEEDED） */
export function createExercise(input: {
  name: string
  bodyPart: string
  recordType: string
}): Promise<Exercise> {
  return apiFetch('/api/exercises', { method: 'POST', body: input, auth: true })
}

/** A-33 自分専用の種目の名前・部位の変更（記録方式は変えられない） */
export function updateExercise(
  id: number,
  input: { name: string; bodyPart: string },
): Promise<Exercise> {
  return apiFetch(`/api/exercises/${id}`, { method: 'PUT', body: input, auth: true })
}

/** A-34 自分専用の種目の削除。記録で使っていれば 409（EXERCISE_IN_USE） */
export function deleteExercise(id: number): Promise<void> {
  return apiFetch(`/api/exercises/${id}`, { method: 'DELETE', auth: true })
}

// ===== 通知（A-120〜A-122） =====

/** A-120 通知一覧（新しい順に20件）。続きは、前のページの nextCursor を渡す */
export function fetchNotifications(cursor?: string): Promise<Page<NotificationItem>> {
  return apiFetch(`/api/notifications${cursorQuery(cursor)}`, { auth: true })
}

/** A-121 未読の数と、フォローのリクエストの数 */
export function fetchUnreadCount(): Promise<UnreadCount> {
  return apiFetch('/api/notifications/unread-count', { auth: true })
}

/** A-122 表示した通知を既読にする（50件まで）。自分の未読の通知だけが既読になる */
export function markNotificationsRead(ids: number[]): Promise<void> {
  return apiFetch('/api/notifications/read', { method: 'POST', body: { ids }, auth: true })
}

// ===== フォロー・プロフィール（A-70〜A-73・A-80） =====

function userPath(username: string): string {
  return `/api/users/${encodeURIComponent(username)}`
}

/** A-70 フォロー（鍵アカウントならリクエスト）。すでにフォロー中・リクエスト中なら今の状態が返る */
export function followUser(username: string): Promise<{ followStatus: FollowStatus }> {
  return apiFetch(`${userPath(username)}/follow`, { method: 'POST', auth: true })
}

/** A-71 フォロー解除・リクエストの取り消し */
export function unfollowUser(username: string): Promise<{ followStatus: FollowStatus }> {
  return apiFetch(`${userPath(username)}/follow`, { method: 'DELETE', auth: true })
}

/** A-72 フォロー中一覧（フォローした新しい順に20件）。続きは、前のページの nextCursor を渡す */
export function fetchFollowing(username: string, cursor?: string): Promise<Page<UserListItem>> {
  return apiFetch(`${userPath(username)}/following${cursorQuery(cursor)}`, { auth: true })
}

/** A-73 フォロワー一覧 */
export function fetchFollowers(username: string, cursor?: string): Promise<Page<UserListItem>> {
  return apiFetch(`${userPath(username)}/followers${cursorQuery(cursor)}`, { auth: true })
}

/** A-80 プロフィール */
export function fetchProfile(username: string): Promise<Profile> {
  return apiFetch(userPath(username), { auth: true })
}

// ===== タイムライン・記録の一覧（A-50〜A-53・A-81） =====

/** タイムラインの種類（フォロー中・全体） */
export type TimelineKind = 'following' | 'all'

function timelinePath(kind: TimelineKind): string {
  return kind === 'all' ? '/api/timeline/all' : '/api/timeline'
}

/** A-50・A-51 タイムライン（投稿日時の新しい順に20件）。続きは、前のページの nextCursor を渡す */
export function fetchTimeline(kind: TimelineKind, cursor?: string): Promise<Page<WorkoutCard>> {
  return apiFetch(`${timelinePath(kind)}${cursorQuery(cursor)}`, { auth: true })
}

/** A-52・A-53 since（1ページ目の一番新しい記録の ID）より後の、新しい投稿の件数（100件まで） */
export function fetchNewPostCount(kind: TimelineKind, since: number): Promise<{ count: number }> {
  return apiFetch(
    `${timelinePath(kind)}/new-count?${new URLSearchParams({ since: String(since) })}`,
    {
      auth: true,
    },
  )
}

/** A-81 そのユーザーの記録一覧（実施日の新しい順に20件） */
export function fetchUserWorkouts(username: string, cursor?: string): Promise<Page<WorkoutCard>> {
  return apiFetch(`${userPath(username)}/workouts${cursorQuery(cursor)}`, { auth: true })
}

function cursorQuery(cursor?: string): string {
  return cursor === undefined ? '' : `?${new URLSearchParams({ cursor })}`
}

/** A-82 プロフィールの編集。アイコンは送ったときだけ差し替わる（縮小した JPEG） */
export function updateProfile(
  input: { displayName: string; bio: string },
  icon?: Blob,
): Promise<Profile> {
  const form = new FormData()
  form.append('data', new Blob([JSON.stringify(input)], { type: 'application/json' }))
  if (icon) {
    form.append('icon', icon, 'icon.jpg')
  }
  return apiFetch('/api/users/me', { method: 'PUT', form, auth: true })
}

/** A-90 週間の集計。date は週の中の日（YYYY-MM-DD） */
export function fetchWeeklyStats(date: string): Promise<WeeklyStats> {
  return apiFetch(`/api/stats/weekly?${new URLSearchParams({ date })}`, { auth: true })
}

/** A-91 月間の集計。month は YYYY-MM */
export function fetchMonthlyStats(month: string): Promise<MonthlyStats> {
  return apiFetch(`/api/stats/monthly?${new URLSearchParams({ month })}`, { auth: true })
}

/** A-92 カレンダー（その月の、自分の記録がある日）。month は YYYY-MM */
export function fetchCalendar(month: string): Promise<CalendarDates> {
  return apiFetch(`/api/stats/calendar?${new URLSearchParams({ month })}`, { auth: true })
}

/** A-93 その日の自分の記録（投稿日時の順にすべて）。date は YYYY-MM-DD */
export function fetchDayWorkouts(date: string): Promise<WorkoutCard[]> {
  return apiFetch(`/api/stats/calendar/workouts?${new URLSearchParams({ date })}`, {
    auth: true,
  })
}

/** A-94 ランニングの集計。period は week・month・year、date は期間の中の日（YYYY-MM-DD） */
export function fetchRunningStats(
  period: 'week' | 'month' | 'year',
  date: string,
): Promise<RunningStats> {
  return apiFetch(`/api/stats/running?${new URLSearchParams({ period, date })}`, { auth: true })
}

/** A-95 自分が記録した種目（部位の順。最後に記録した日つき） */
export function fetchUsedExercises(): Promise<UsedExercise[]> {
  return apiFetch('/api/stats/exercises', { auth: true })
}

/** A-96 種目ごとの推移と自己ベスト。見えない・ない種目は 404（EXERCISE_NOT_FOUND） */
export function fetchExerciseTrend(exerciseId: number, range: TrendRange): Promise<ExerciseTrend> {
  return apiFetch(`/api/stats/exercises/${exerciseId}?${new URLSearchParams({ range })}`, {
    auth: true,
  })
}
