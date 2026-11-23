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
