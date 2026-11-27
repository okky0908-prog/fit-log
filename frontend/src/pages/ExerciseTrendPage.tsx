import { useQuery } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router'
import { BackButton } from '../components/BackButton'
import { StatsChart } from '../components/StatsChart'
import { UsedExerciseList } from '../components/UsedExerciseList'
import { ApiError, errorMessage, fetchExerciseTrend, fetchUsedExercises } from '../lib/api/client'
import type { ExerciseTrend, TrendRange } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import type { Units } from '../lib/format/workout'
import {
  bestLabel,
  formatTrendValue,
  rangeFromParam,
  type TrendView,
  trendPoints,
  trendRow,
  trendViews,
  viewFromParam,
} from '../lib/stats/trend'
import styles from './RecordsPage.module.css'

const RANGES: { range: TrendRange; label: string }[] = [
  { range: '3m', label: '3か月' },
  { range: '1y', label: '1年' },
  { range: 'all', label: '全期間' },
]

/**
 * S-22 種目ごとの推移（/records/exercises/:exerciseId）（docs/screens.md S-22、F-84・F-85）。
 * 期間は URL の `?range=`（3か月なら入れない）、グラフの値は `?view=`（最初の値なら入れない）に入れる
 */
export function ExerciseTrendPage() {
  const { user } = useAuth()
  const units: Units = {
    weightUnit: user?.weightUnit ?? 'kg',
    distanceUnit: user?.distanceUnit ?? 'km',
  }
  const params = useParams()
  // 正の整数でなければ、通信せずに「見つかりません」
  const exerciseId = /^[1-9]\d*$/.test(params.exerciseId ?? '') ? Number(params.exerciseId) : null
  const [searchParams, setSearchParams] = useSearchParams()
  const range = rangeFromParam(searchParams.get('range'))
  const view = searchParams.get('view')

  const query = useQuery({
    queryKey: ['stats', 'trend', exerciseId, range],
    queryFn: () => fetchExerciseTrend(exerciseId ?? 0, range),
    enabled: exerciseId !== null,
  })
  const notFound =
    exerciseId === null ||
    (query.error instanceof ApiError && query.error.code === 'EXERCISE_NOT_FOUND')

  const rangeHref = (next: TrendRange) => {
    const search = new URLSearchParams()
    if (next !== '3m') search.set('range', next)
    if (view !== null) search.set('view', view)
    const path = `/records/exercises/${exerciseId}`
    return search.size === 0 ? path : `${path}?${search}`
  }

  return (
    <>
      <BackButton fallback="/records/exercises">種目ごとの推移</BackButton>
      {(notFound || !query.data) && <h1 className={styles.title}>種目ごとの推移</h1>}
      {notFound ? (
        <div className={styles.message}>
          <p>この種目は見つかりません</p>
          <Link to="/records/exercises">種目の一覧へ</Link>
        </div>
      ) : query.error ? (
        <div className={styles.message}>
          <p>{errorMessage(query.error)}</p>
          <button type="button" className="button-secondary" onClick={() => void query.refetch()}>
            もう一度読み込む
          </button>
        </div>
      ) : !query.data ? (
        <p className={styles.message}>読み込み中…</p>
      ) : (
        <>
          <div className={styles.head}>
            <h1 className={styles.title}>{query.data.exercise.name}</h1>
            <ExerciseSwitch key={exerciseId} range={range} />
          </div>
          <div role="tablist" className={styles.tabs}>
            {RANGES.map((r) => (
              <Link
                key={r.range}
                to={rangeHref(r.range)}
                replace
                role="tab"
                aria-selected={r.range === range}
                className={styles.tab}
              >
                {r.label}
              </Link>
            ))}
          </div>
          <div role="tabpanel" aria-label={RANGES.find((r) => r.range === range)?.label}>
            <TrendView
              trend={query.data}
              view={viewFromParam(view, query.data.exercise)}
              units={units}
              onView={(next, isDefault) =>
                setSearchParams(
                  (current) => {
                    if (isDefault) current.delete('view')
                    else current.set('view', next)
                    return current
                  },
                  { replace: true },
                )
              }
            />
          </div>
        </>
      )}
    </>
  )
}

/** 「種目を変える」：押すと記録した種目の一覧を出し、選ぶとその種目の推移へ（期間はそのまま） */
function ExerciseSwitch({ range }: { range: TrendRange }) {
  const [open, setOpen] = useState(false)
  const panelId = useId()
  const used = useQuery({
    queryKey: ['stats', 'exercises'],
    queryFn: fetchUsedExercises,
    enabled: open,
  })
  return (
    <>
      <button
        type="button"
        className="button-secondary"
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen(!open)}
      >
        種目を変える
      </button>
      {open && (
        <div id={panelId} className={styles.switchPanel}>
          {used.error ? (
            <p className={styles.empty}>{errorMessage(used.error)}</p>
          ) : !used.data ? (
            <p className={styles.empty}>読み込み中…</p>
          ) : (
            <UsedExerciseList
              used={used.data}
              hrefFor={(id) =>
                range === '3m'
                  ? `/records/exercises/${id}`
                  : `/records/exercises/${id}?range=${range}`
              }
            />
          )}
        </div>
      )}
    </>
  )
}

interface TrendViewProps {
  trend: ExerciseTrend
  view: TrendView
  units: Units
  onView: (view: TrendView, isDefault: boolean) => void
}

function TrendView({ trend, view, units, onView }: TrendViewProps) {
  const bestId = useId()
  const chartId = useId()
  const listId = useId()
  const { exercise } = trend
  const views = trendViews(exercise)
  const viewLabel = views.find((v) => v.view === view)?.label ?? ''
  const newestFirst = [...trend.points].reverse()

  return (
    <>
      {exercise.recordType === 'weight_reps' && (
        <section aria-labelledby={bestId} className={styles.card}>
          <h2 id={bestId} className="visually-hidden">
            ベスト
          </h2>
          <dl className={styles.bestList}>
            <div className={styles.bestItem}>
              <dt>🏆 自己ベスト</dt>
              <dd>
                {trend.personalBest === null
                  ? '—'
                  : bestLabel(trend.personalBest.weightKg, trend.personalBest.performedOn, units)}
              </dd>
            </div>
            <div className={styles.bestItem}>
              <dt>推定 1RM の最大</dt>
              <dd>
                {trend.bestE1rm === null
                  ? '—'
                  : bestLabel(trend.bestE1rm.e1rmKg, trend.bestE1rm.performedOn, units)}
              </dd>
            </div>
          </dl>
          <p className={styles.note}>推定 1RM は推定値です（1〜10回のセットから Epley 式で計算）</p>
        </section>
      )}

      <section aria-labelledby={chartId} className={styles.card}>
        <div className={styles.cardHead}>
          <h2 id={chartId} className={styles.cardTitle}>
            推移
          </h2>
          <div className={styles.segments}>
            {views.map((v, index) => (
              <button
                key={v.view}
                type="button"
                className={styles.segment}
                aria-pressed={v.view === view}
                onClick={() => onView(v.view, index === 0)}
              >
                {v.label}
              </button>
            ))}
          </div>
        </div>
        <StatsChart
          type="line"
          title={`${viewLabel}の推移`}
          labelHeader="日付"
          valueName={viewLabel}
          points={trendPoints(trend, view, units)}
          format={(value) => formatTrendValue(value, view, exercise, units)}
        />
      </section>

      <section aria-labelledby={listId} className={styles.card}>
        <div className={styles.cardHead}>
          <h2 id={listId} className={styles.cardTitle}>
            記録の一覧
          </h2>
          <span className={styles.note}>新しい順</span>
        </div>
        {newestFirst.length === 0 ? (
          <p className={styles.empty}>この期間の記録はありません</p>
        ) : (
          <ul className={styles.trendRows}>
            {newestFirst.map((point) => (
              <li key={point.date}>
                <Link to={`/workouts/${point.workoutId}`} className={styles.trendRow}>
                  <span className={styles.trendDate}>{point.date.replaceAll('-', '/')}</span>
                  <span>{trendRow(point, exercise, units)}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
