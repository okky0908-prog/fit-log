// API のデータの形（docs/api.md）。段階 1 で使うもの。
// openapi.yaml からの型の自動生成（docs/api.md の冒頭）は、API が増えたところで入れる

/** エラーの code（docs/api.md「エラー」。画面で見分けるものだけ。ほかの値も届くので string も受ける） */
export type ApiErrorCode =
  | 'VALIDATION_FAILED'
  | 'MALFORMED_REQUEST'
  | 'INVALID_TOKEN'
  | 'UNAUTHENTICATED'
  | 'INVALID_CREDENTIALS'
  | 'SESSION_EXPIRED'
  | 'ALREADY_REGISTERED'
  | 'ALREADY_VERIFIED'
  | 'TOO_MANY_LOGIN_ATTEMPTS'
  | 'TOO_MANY_EMAILS'
  | 'SERVICE_UNAVAILABLE'
  | 'SERVER_BUSY'
  | 'WORKOUT_NOT_FOUND'
  | 'EXERCISE_NOT_FOUND'
  | 'EXERCISE_NAME_TAKEN'
  | 'EXERCISE_LIMIT_EXCEEDED'
  | 'EXERCISE_IN_USE'
  | 'FORBIDDEN'
  | 'USER_NOT_FOUND'
  | 'CANNOT_FOLLOW_SELF'
  | 'PRIVATE_ACCOUNT'
  | (string & {})

/** 項目ごとのエラー（入力チェック・登録済み） */
export interface FieldErrorBody {
  field: string
  message: string
}

/** エラーのレスポンス（docs/api.md「エラー」） */
export interface ApiErrorBody {
  status: number
  code: ApiErrorCode
  message: string
  errors: FieldErrorBody[]
}

/** ログイン中のユーザー（A-03） */
export interface Me {
  username: string
  displayName: string
  iconUrl: string | null
  bio: string | null
  email: string
  emailVerified: boolean
  isPrivate: boolean
  weightUnit: 'kg' | 'lb'
  distanceUnit: 'km' | 'mi'
}

/** 新規登録・ログイン・再発行のレスポンス（A-01・A-02・A-04） */
export interface AuthResponse {
  accessToken: string
  user: Me
}

export interface SignupInput {
  username: string
  displayName: string
  email: string
  password: string
}

export interface LoginInput {
  email: string
  password: string
}

/** A-06 のレスポンス。email_changed（メールアドレスの変更）は段階 5 で使う */
export interface VerifyEmailResult {
  result: 'verified' | 'email_changed'
  email: string
}

/** 記録方式（docs/features.md「種目」） */
export type RecordType = 'weight_reps' | 'reps' | 'time_distance'

export type Visibility = 'public' | 'followers' | 'private'

/** セット（kg・km・秒） */
export interface WorkoutSet {
  weightKg: number | null
  reps: number | null
  durationSec: number | null
  distanceKm: number | null
  /** 自己ベストを出したセットか（段階 2 までは false） */
  personalBest: boolean
}

export interface WorkoutExercise {
  exerciseId: number
  name: string
  bodyPart: string
  recordType: RecordType
  isRunning: boolean
  /** その記録で、その種目の自己ベストを更新したか */
  personalBest: boolean
  sets: WorkoutSet[]
}

export interface WorkoutImage {
  id: number
  /** 署名付き URL（期限つき。画面は保存しない） */
  url: string
  sortOrder: number
}

/** 記録の詳細（docs/api.md「Workout」。A-20〜A-22） */
export interface Workout {
  id: number
  author: { username: string; displayName: string; iconUrl: string | null; isPrivate: boolean }
  performedOn: string
  createdAt: string
  editedAt: string | null
  body: string
  durationMin: number | null
  visibility: Visibility
  exercises: WorkoutExercise[]
  images: WorkoutImage[]
  likeCount: number
  likedByMe: boolean
  commentCount: number
  mine: boolean
}

/** 一覧の種目の要約（docs/api.md「WorkoutCard」）。記録方式で使わない項目は null */
export interface ExerciseSummary {
  exerciseId: number
  name: string
  recordType: RecordType
  personalBest: boolean
  setCount: number
  /** 全セットが同じ値か（時間・距離は null） */
  uniform: boolean | null
  /** 重量 × 回数：全セットが同じならその値、違えば最大のセットの値 */
  weightKg: number | null
  /** 重量 × 回数：weightKg と同じセットの回数。回数のみ：全セットが同じときの回数 */
  reps: number | null
  /** 回数のみで、値が違うときの合計 */
  totalReps: number | null
  /** 時間・距離：時間（2セット以上なら合計） */
  durationSec: number | null
  /** 時間・距離：距離（2セット以上なら合計）。距離がなければ null */
  distanceKm: number | null
  /** 1セットで距離があるときだけ */
  paceSecPerKm: number | null
}

/** 記録の一覧の1件（docs/api.md「WorkoutCard」。A-50・A-51・A-81） */
export interface WorkoutCard {
  id: number
  author: UserSummary
  performedOn: string
  createdAt: string
  editedAt: string | null
  body: string
  visibility: Visibility
  /** 先頭5種目 */
  exerciseSummaries: ExerciseSummary[]
  /** 6種目目からの種目の数（「ほか N 種目」） */
  otherExerciseCount: number
  images: WorkoutImage[]
  likeCount: number
  likedByMe: boolean
  commentCount: number
  mine: boolean
}

/** 種目（docs/api.md A-30・A-31） */
export interface Exercise {
  id: number
  name: string
  bodyPart: string
  recordType: RecordType
  isRunning: boolean
  /** 自分専用の種目か */
  custom: boolean
}

/** 投稿者・操作した人など（docs/api.md「UserSummary」） */
export interface UserSummary {
  username: string
  displayName: string
  iconUrl: string | null
  isPrivate: boolean
}

/** 自分から相手へのフォローの状態（A-70〜A-73・A-80） */
export type FollowStatus = 'none' | 'requested' | 'following'

/** ユーザー一覧の1人（A-72・A-73。UserSummary に、自己紹介・自分から見た状態・自分自身かを足したもの） */
export interface UserListItem extends UserSummary {
  bio: string | null
  followStatus: FollowStatus
  me: boolean
}

/** プロフィール（A-80） */
export interface Profile extends UserSummary {
  bio: string | null
  followingCount: number
  followerCount: number
  me: boolean
  followStatus: FollowStatus
  /** 記録・公開した目標・フォロー一覧を見られるか（本人・鍵アカウントでない・承認済みのフォロワー） */
  canViewRecords: boolean
}

export type NotificationType =
  | 'like'
  | 'comment'
  | 'reply'
  | 'follow'
  | 'follow_request'
  | 'follow_accepted'
  | 'goal_achieved'
  | 'goal_cheer'

/** 通知（docs/api.md「Notification」。A-120）。文言は画面が type から作る */
export interface NotificationItem {
  id: number
  type: NotificationType
  createdAt: string
  read: boolean
  /** 操作した人。目標の達成は null */
  actor: UserSummary | null
  /** 対象の投稿。bodyExcerpt は先頭の30文字（超えたら「…」） */
  workout: { id: number; bodyExcerpt: string } | null
  comment: { id: number; bodyExcerpt: string } | null
  goal: { id: number; title: string } | null
}

/** 一覧の1ページ（docs/api.md「ページング」） */
export interface Page<T> {
  items: T[]
  nextCursor: string | null
  hasNext: boolean
}

/** A-121 未読の数 */
export interface UnreadCount {
  unreadCount: number
  followRequestCount: number
}
