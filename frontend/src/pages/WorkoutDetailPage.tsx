import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { CommentCount } from '../components/CommentCount'
import { EmojiIcon } from '../components/EmojiIcon'
import { LikeButton } from '../components/LikeButton'
import { WorkoutHeader } from '../components/WorkoutHeader'
import { WorkoutImages } from '../components/WorkoutImages'
import { ApiError, errorMessage, fetchWorkout } from '../lib/api/client'
import type { Workout, WorkoutExercise } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { bodyPartLabel, formatSet, type Units } from '../lib/format/workout'
import styles from './WorkoutDetailPage.module.css'

/**
 * S-12 投稿詳細（docs/screens.md）。
 *
 * コメント欄（docs/test-plan.md 8.2 の 13-4）、消費カロリー（段階 5）、ハッシュタグのリンク（段階 4）、
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
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const units: Units = {
    weightUnit: user?.weightUnit ?? 'kg',
    distanceUnit: user?.distanceUnit ?? 'km',
  }
  // 「◯分前に投稿」の基準。描き直すたびに変わらないよう、開いたときの時刻を持つ
  const [now] = useState(() => new Date())

  return (
    <article className={styles.card}>
      <WorkoutHeader
        workout={workout}
        now={now}
        onDeleted={() => {
          queryClient.removeQueries({ queryKey: ['workout', workout.id] })
          void navigate('/')
        }}
      />

      {workout.body && <p className={styles.body}>{workout.body}</p>}
      {workout.durationMin !== null && (
        <p className={styles.duration}>トレーニング時間 {workout.durationMin}分</p>
      )}

      {workout.exercises.map((exercise, index) => (
        <ExerciseView key={index} exercise={exercise} units={units} />
      ))}

      {workout.images.length > 0 && <WorkoutImages urls={workout.images.map((i) => i.url)} />}

      <p className={styles.counts}>
        <CommentCount count={workout.commentCount} />
        <LikeButton
          workoutId={workout.id}
          likeCount={workout.likeCount}
          likedByMe={workout.likedByMe}
        />
      </p>
    </article>
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
            {set.personalBest && <EmojiIcon emoji="🏆" label="自己ベスト" />}
          </li>
        ))}
      </ol>
    </section>
  )
}
