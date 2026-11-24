import { useQueryClient } from '@tanstack/react-query'
import type { MouseEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import type { WorkoutCard as Card } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { formatExerciseSummary, type Units } from '../lib/format/workout'
import { WorkoutHeader } from './WorkoutHeader'
import { WorkoutImages } from './WorkoutImages'
import styles from './WorkoutCard.module.css'

interface Props {
  workout: Card
  /** 「◯分前に投稿」の基準の時刻（一覧を開いたとき） */
  now: Date
}

/**
 * 投稿カード（docs/screens.md「投稿カード」。タイムライン・プロフィールで共通）。カードの余白を押すと S-12 投稿詳細へ。
 * キーボード・読み上げでは、実施日のリンクで詳細へ移る。
 *
 * 💪 を押して付ける・取り消す、💬 でコメント欄へ移るのは 13、ハッシュタグのリンクは段階 4 で足す
 */
export function WorkoutCard({ workout, now }: Props) {
  const { user } = useAuth()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const units: Units = {
    weightUnit: user?.weightUnit ?? 'kg',
    distanceUnit: user?.distanceUnit ?? 'km',
  }
  const detail = `/workouts/${workout.id}`

  // リンク・ボタン（と、拡大表示などそこから開いたもの）を押したときは、そちらの動きを優先する
  const openDetail = (event: MouseEvent<HTMLElement>) => {
    const target = event.target as HTMLElement
    if (target.closest('a, button, [role="dialog"]')) return
    void navigate(detail)
  }

  return (
    // 余白を押したときの移動はマウス・タッチのための近道。キーボードでは実施日のリンクで移れる
    // oxlint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-noninteractive-element-interactions
    <article
      className={styles.card}
      aria-label={`${workout.author.displayName}の記録`}
      onClick={openDetail}
    >
      <WorkoutHeader
        workout={workout}
        now={now}
        onDeleted={() => {
          void queryClient.invalidateQueries({ queryKey: ['timeline'] })
          void queryClient.invalidateQueries({ queryKey: ['users'] })
        }}
        renderPerformedOn={(label) => (
          <Link to={detail} className={styles.dateLink}>
            {label}
          </Link>
        )}
      />

      {workout.body && <p className={styles.body}>{workout.body}</p>}

      <ul className={styles.summaries}>
        {workout.exerciseSummaries.map((summary, index) => (
          <li key={index} className={styles.summary}>
            <span className={styles.exerciseName}>{summary.name}</span>
            <span>{formatExerciseSummary(summary, units)}</span>
            {summary.personalBest && <span className={styles.best}>🏆自己ベスト更新</span>}
          </li>
        ))}
      </ul>
      {workout.otherExerciseCount > 0 && (
        <p className={styles.others}>ほか {workout.otherExerciseCount} 種目</p>
      )}

      {workout.images.length > 0 && <WorkoutImages urls={workout.images.map((i) => i.url)} />}

      <p className={styles.counts}>
        <Count emoji="💬" label="コメント" count={workout.commentCount} />
        <Count emoji="💪" label="ナイストレーニング" count={workout.likeCount} />
      </p>
    </article>
  )
}

/** 💬・💪 と数。0 なら数字を出さない */
function Count({ emoji, label, count }: { emoji: string; label: string; count: number }) {
  return (
    <span title={label} className={styles.count}>
      <span aria-hidden="true">{emoji}</span>
      <span className="visually-hidden">{label}</span>
      {count > 0 && <span>{count}</span>}
    </span>
  )
}
