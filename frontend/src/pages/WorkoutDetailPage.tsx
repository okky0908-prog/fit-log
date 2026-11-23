import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ImageViewer } from '../components/ImageViewer'
import { LockMark } from '../components/LockMark'
import { UserLink } from '../components/UserLink'
import { ApiError, deleteWorkout, errorMessage, fetchWorkout } from '../lib/api/client'
import type { Visibility, Workout, WorkoutExercise } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { formatPerformedOn, formatPostedAgo } from '../lib/format/date'
import { bodyPartLabel, formatSet, type Units } from '../lib/format/workout'
import { useToast } from '../lib/toast'
import styles from './WorkoutDetailPage.module.css'

const VISIBILITY: Record<Visibility, { icon: string; label: string }> = {
  public: { icon: '🌐', label: '公開' },
  followers: { icon: '👥', label: 'フォロワー限定' },
  private: { icon: '🔒', label: '自分のみ' },
}

/**
 * S-12 投稿詳細（docs/screens.md）。
 *
 * ナイストレーニング・コメント（docs/test-plan.md 8.2 の 13）、消費カロリー（段階 5）、ハッシュタグのリンク（段階 4）、
 * 投稿者へのリンク（11）、`…` メニューの「複製」「テンプレートとして保存」（段階 4）は、それぞれの PR で足す
 */
export function WorkoutDetailPage() {
  const { workoutId } = useParams()
  const id = /^\d+$/.test(workoutId ?? '') ? Number(workoutId) : null
  const query = useQuery({
    queryKey: ['workout', id],
    queryFn: () => fetchWorkout(id as number),
    enabled: id !== null,
  })

  return (
    <>
      <BackButton />
      {id === null || isNotFound(query.error) ? (
        <p className={styles.message}>この記録は見つかりません</p>
      ) : query.error ? (
        <div className={styles.message}>
          <p>{errorMessage(query.error)}</p>
          <button type="button" className="button-secondary" onClick={() => void query.refetch()}>
            もう一度読み込む
          </button>
        </div>
      ) : query.data ? (
        <WorkoutView workout={query.data} />
      ) : (
        <p className={styles.message}>読み込み中…</p>
      )}
    </>
  )
}

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'WORKOUT_NOT_FOUND'
}

/** ← 直前の画面に戻る。URL を直接開いたなど、アプリの中に戻る先がなければホームへ */
function BackButton() {
  const navigate = useNavigate()
  const location = useLocation()
  // 最初に開いた画面の location.key は 'default'
  const hasPrevious = location.key !== 'default'
  return (
    <button
      type="button"
      className={styles.back}
      aria-label="戻る"
      onClick={() => (hasPrevious ? void navigate(-1) : void navigate('/'))}
    >
      ← 記録
    </button>
  )
}

function WorkoutView({ workout }: { workout: Workout }) {
  const { user } = useAuth()
  const units: Units = {
    weightUnit: user?.weightUnit ?? 'kg',
    distanceUnit: user?.distanceUnit ?? 'km',
  }
  // 「◯分前に投稿」の基準。描き直すたびに変わらないよう、開いたときの時刻を持つ
  const [now] = useState(() => new Date())
  const postedAgo = formatPostedAgo(workout.createdAt, workout.performedOn, now)
  const visibility = VISIBILITY[workout.visibility]

  return (
    <article className={styles.card}>
      <header className={styles.head}>
        <div className={styles.byline}>
          <UserLink username={workout.author.username} plain>
            <Avatar
              displayName={workout.author.displayName}
              iconUrl={workout.author.iconUrl}
              size={40}
            />
            {/* アイコンは読み上げないので、リンクの名前を補う */}
            <span className="visually-hidden">{workout.author.displayName}のプロフィール</span>
          </UserLink>
          <div>
            <p className={styles.author}>
              <UserLink username={workout.author.username} className={styles.displayName}>
                {workout.author.displayName}
              </UserLink>
              {workout.author.isPrivate && <LockMark />}
              <UserLink username={workout.author.username} className={styles.username}>
                @{workout.author.username}
              </UserLink>
            </p>
            <p className={styles.meta}>
              <span>{formatPerformedOn(workout.performedOn, now)}</span>
              {postedAgo && <span>{postedAgo}</span>}
              {workout.editedAt && <span>編集済み</span>}
              <Icon emoji={visibility.icon} label={visibility.label} />
            </p>
          </div>
        </div>
        {workout.mine && <OwnerMenu workoutId={workout.id} />}
      </header>

      {workout.body && <p className={styles.body}>{workout.body}</p>}
      {workout.durationMin !== null && (
        <p className={styles.duration}>トレーニング時間 {workout.durationMin}分</p>
      )}

      {workout.exercises.map((exercise, index) => (
        <ExerciseView key={index} exercise={exercise} units={units} />
      ))}

      {workout.images.length > 0 && <WorkoutImages urls={workout.images.map((i) => i.url)} />}
    </article>
  )
}

/** 絵文字のアイコン。絵文字は読み上げずに、意味（label）を読み上げる。マウスを乗せると意味を出す */
function Icon({ emoji, label }: { emoji: string; label: string }) {
  return (
    <span title={label}>
      <span aria-hidden="true">{emoji}</span>
      <span className="visually-hidden">{label}</span>
    </span>
  )
}

/** 画像の並び（1〜4枚）。押すと拡大表示 */
function WorkoutImages({ urls }: { urls: string[] }) {
  const [viewing, setViewing] = useState<number | null>(null)
  return (
    <>
      <div className={`${styles.images} ${styles[`images${urls.length}`]}`}>
        {urls.map((url, index) => (
          <button
            key={url}
            type="button"
            className={styles.imageButton}
            aria-label={`画像 ${index + 1} / ${urls.length} を拡大`}
            onClick={() => setViewing(index)}
          >
            <img src={url} alt={`画像 ${index + 1} / ${urls.length}`} className={styles.image} />
          </button>
        ))}
      </div>
      {viewing !== null && (
        <ImageViewer
          urls={urls}
          index={viewing}
          onMove={setViewing}
          onClose={() => setViewing(null)}
        />
      )}
    </>
  )
}

function ExerciseView({ exercise, units }: { exercise: WorkoutExercise; units: Units }) {
  const title = `${exercise.name}（${bodyPartLabel(exercise.bodyPart)}）`
  return (
    <section className={styles.exercise} aria-label={title}>
      <h2 className={styles.exerciseName}>
        <span>■ {title}</span>
        {exercise.personalBest && <span className={styles.best}>🏆自己ベスト更新</span>}
      </h2>
      <ol className={styles.sets}>
        {exercise.sets.map((set, index) => (
          <li key={index} className={styles.set}>
            <span className={styles.setNumber}>{index + 1}</span>
            {formatSet(set, exercise.recordType, exercise.isRunning, units).map((part) => (
              <span key={part}>{part}</span>
            ))}
            {set.personalBest && <Icon emoji="🏆" label="自己ベスト" />}
          </li>
        ))}
      </ol>
    </section>
  )
}

/** 自分の記録の `…` メニュー（docs/screens.md「投稿カード」）。「複製」「テンプレートとして保存」は段階 4 で足す */
function OwnerMenu({ workoutId }: { workoutId: number }) {
  const [open, setOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const removal = useMutation({
    mutationFn: () => deleteWorkout(workoutId),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: ['workout', workoutId] })
      toast('記録を削除しました')
      void navigate('/')
    },
    onError: (err) => {
      setConfirming(false)
      toast(errorMessage(err))
    },
  })

  return (
    <div className={styles.menu}>
      <button
        type="button"
        className={styles.menuButton}
        aria-label="メニュー"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        …
      </button>
      {open && (
        <ul role="menu" className={styles.menuList}>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              className={styles.menuItemPlain}
              onClick={() => void navigate(`/workouts/${workoutId}/edit`)}
            >
              編集
            </button>
          </li>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              className={styles.menuItem}
              onClick={() => {
                setOpen(false)
                setConfirming(true)
              }}
            >
              削除
            </button>
          </li>
        </ul>
      )}
      {confirming && (
        <ConfirmDialog
          title="この記録を削除しますか？"
          description="種目・セット・画像・コメントも削除されます。元に戻せません"
          confirmLabel="削除する"
          busy={removal.isPending}
          onConfirm={() => removal.mutate()}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  )
}
