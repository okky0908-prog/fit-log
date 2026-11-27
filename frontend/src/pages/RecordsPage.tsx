import { useQuery } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { PeriodSwitch } from '../components/PeriodSwitch'
import { RecordsCalendar } from '../components/RecordsCalendar'
import { StatsChart } from '../components/StatsChart'
import { WorkoutCard } from '../components/WorkoutCard'
import {
  errorMessage,
  fetchCalendar,
  fetchDayWorkouts,
  fetchMonthlyStats,
  fetchUserWorkouts,
  fetchWeeklyStats,
} from '../lib/api/client'
import type { BodyPartCount, StatsSummary } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { formatPerformedOn, tokyoDate } from '../lib/format/date'
import type { Units } from '../lib/format/workout'
import { dayFromParam } from '../lib/stats/calendar'
import {
  addDays,
  addMonths,
  hasNextMonth,
  hasNextWeek,
  monthFromParam,
  monthLabel,
  weekFromParam,
  weekLabel,
  weekStartOf,
} from '../lib/stats/period'
import {
  bodyPartRows,
  type ChartPoint,
  dailyPoints,
  formatMetric,
  type Metric,
  summaryItems,
  weeklyPoints,
} from '../lib/stats/stats'
import styles from './RecordsPage.module.css'

export type RecordsKind = 'week' | 'month'

const TABS: { kind: RecordsKind; label: string; path: string }[] = [
  { kind: 'week', label: '週間', path: '/records' },
  { kind: 'month', label: '月間', path: '/records/month' },
]

const METRICS: { metric: Metric; label: string }[] = [
  { metric: 'volume', label: '総挙上量' },
  { metric: 'duration', label: '運動時間' },
]

/** 表示する期間の集計を、週間・月間で同じ形にしたもの */
interface PeriodStats {
  summary: StatsSummary
  previous: StatsSummary
  points: (metric: Metric) => ChartPoint[]
  bodyParts: BodyPartCount[]
}

/**
 * S-20 記録（ダッシュボード）の週間（/records）・月間（/records/month）（docs/screens.md）。
 * 期間は URL（`?week=`・`?month=`）に、グラフの値（総挙上量・運動時間）は `?metric=duration` に入れる。
 *
 * カレンダーの月と記録を出す日は `?cal=`・`?day=` に入れる（週間・月間の期間とは別に動かせる）。
 *
 * 消費カロリー（段階 5）、目標（段階 3）、種目へのリンク（4-2）は、それぞれの PR で足す
 */
export function RecordsPage({ kind }: { kind: RecordsKind }) {
  const { user } = useAuth()
  const units: Units = {
    weightUnit: user?.weightUnit ?? 'kg',
    distanceUnit: user?.distanceUnit ?? 'km',
  }
  const [searchParams, setSearchParams] = useSearchParams()
  const metric: Metric = searchParams.get('metric') === 'duration' ? 'duration' : 'volume'
  // 「今日」（日本時間）。描き直すたびに変わらないよう、開いたときの日付を持つ
  const [today] = useState(() => tokyoDate(new Date()))
  const period = usePeriod(kind, searchParams, today)

  // 記録が1件もないか（自分の記録一覧の1ページ目で確かめる）。取れなければ、集計を出す
  const hasAny = useQuery({
    queryKey: ['records', 'has-any'],
    queryFn: async () => (await fetchUserWorkouts(user?.username ?? '')).items.length > 0,
  })

  const stats = useQuery({
    queryKey: ['stats', kind, period.key],
    queryFn: async (): Promise<PeriodStats> => {
      if (kind === 'week') {
        const weekly = await fetchWeeklyStats(period.key)
        return { ...weekly, points: (m) => dailyPoints(weekly, m, units) }
      }
      const monthly = await fetchMonthlyStats(period.key)
      return { ...monthly, points: (m) => weeklyPoints(monthly, m, units) }
    },
  })

  return (
    <>
      <div className={styles.head}>
        <h1 className={styles.title}>記録</h1>
        <Link to="/records/running" className={styles.headLink}>
          ランニング ›
        </Link>
      </div>
      <div role="tablist" className={styles.tabs}>
        {TABS.map((tab) => (
          <Link
            key={tab.kind}
            to={tab.path}
            replace
            role="tab"
            aria-selected={tab.kind === kind}
            className={styles.tab}
          >
            {tab.label}
          </Link>
        ))}
      </div>
      <div role="tabpanel" aria-label={kind === 'week' ? '週間' : '月間'}>
        <PeriodSwitch {...period.switchProps} />
        {hasAny.data === false ? (
          <div className={styles.message}>
            <p>記録をつけると、ここに週・月の集計が表示されます</p>
            <Link to="/workouts/new" className="button-primary">
              ＋ 記録する
            </Link>
          </div>
        ) : stats.error ? (
          <div className={styles.message}>
            <p>{errorMessage(stats.error)}</p>
            <button type="button" className="button-secondary" onClick={() => void stats.refetch()}>
              もう一度読み込む
            </button>
          </div>
        ) : !stats.data ? (
          <p className={styles.message}>読み込み中…</p>
        ) : (
          <StatsView
            kind={kind}
            stats={stats.data}
            metric={metric}
            units={units}
            onMetric={(next) =>
              setSearchParams(
                (params) => {
                  if (next === 'duration') params.set('metric', 'duration')
                  else params.delete('metric')
                  return params
                },
                { replace: true },
              )
            }
          />
        )}
      </div>
      {hasAny.data !== false && <CalendarSection kind={kind} params={searchParams} today={today} />}
    </>
  )
}

/** カレンダーと、選んだ日の記録 */
function CalendarSection({
  kind,
  params,
  today,
}: {
  kind: RecordsKind
  params: URLSearchParams
  today: string
}) {
  const calendarId = useId()
  const month = monthFromParam(params.get('cal'), today)
  const day = dayFromParam(params.get('day'), month, today)
  const calendar = useQuery({
    queryKey: ['stats', 'calendar', month],
    queryFn: () => fetchCalendar(month),
  })
  // 週間・月間の期間とグラフの値はそのままにして、カレンダーの月と日だけを変える（今月なら月は入れない）
  const hrefFor = (nextMonth: string, nextDay: string | null) => {
    const next = new URLSearchParams(params)
    next.delete('cal')
    next.delete('day')
    if (nextMonth !== today.slice(0, 7)) next.set('cal', nextMonth)
    if (nextDay !== null) next.set('day', nextDay)
    const path = kind === 'week' ? '/records' : '/records/month'
    return next.size === 0 ? path : `${path}?${next}`
  }

  return (
    <>
      <section aria-labelledby={calendarId} className={styles.card}>
        <h2 id={calendarId} className={styles.cardTitle}>
          カレンダー
        </h2>
        <RecordsCalendar
          month={month}
          today={today}
          dates={new Set(calendar.data?.dates ?? [])}
          selected={day}
          hrefFor={hrefFor}
        />
      </section>
      {day !== null && <DayWorkouts date={day} />}
    </>
  )
}

/** その日の自分の記録（投稿カード） */
function DayWorkouts({ date }: { date: string }) {
  const headingId = useId()
  // 「◯分前に投稿」の基準。描き直すたびに変わらないよう、開いたときの時刻を持つ
  const [now] = useState(() => new Date())
  const query = useQuery({
    // ナイストレーニング・コメントのあとに書き換える対象（lib/workout/workoutCache.ts）
    queryKey: ['stats', 'calendar-day', date],
    queryFn: () => fetchDayWorkouts(date),
  })
  return (
    <section aria-labelledby={headingId}>
      <h2 id={headingId} className={styles.cardTitle}>
        {formatPerformedOn(date, now)}の記録
      </h2>
      {query.error ? (
        <div className={styles.message}>
          <p>{errorMessage(query.error)}</p>
          <button type="button" className="button-secondary" onClick={() => void query.refetch()}>
            もう一度読み込む
          </button>
        </div>
      ) : !query.data ? (
        <p className={styles.message}>読み込み中…</p>
      ) : query.data.length === 0 ? (
        <p className={styles.empty}>この日の記録はありません</p>
      ) : (
        <ul className={styles.workouts}>
          {query.data.map((workout) => (
            <li key={workout.id}>
              <WorkoutCard workout={workout} now={now} />
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** URL から表示する期間を決め、期間の切り替えのリンクを作る（グラフの値の選び方と、カレンダーの月・日は残す） */
function usePeriod(kind: RecordsKind, params: URLSearchParams, today: string) {
  const keep: Record<string, string> = {}
  for (const name of ['metric', 'cal', 'day']) {
    const value = params.get(name)
    if (value !== null) keep[name] = value
  }
  const href = (path: string, query: Record<string, string>) => {
    const search = new URLSearchParams({ ...query, ...keep }).toString()
    return search === '' ? path : `${path}?${search}`
  }
  if (kind === 'week') {
    const monday = weekFromParam(params.get('week'), today)
    const next = addDays(monday, 7)
    return {
      key: monday,
      switchProps: {
        label: weekLabel(monday),
        unit: '週',
        previous: href('/records', { week: addDays(monday, -7) }),
        next: !hasNextWeek(monday, today)
          ? null
          : next === weekStartOf(today)
            ? href('/records', {})
            : href('/records', { week: next }),
        current: monday === weekStartOf(today) ? null : href('/records', {}),
        currentLabel: '今週',
      },
    }
  }
  const month = monthFromParam(params.get('month'), today)
  const next = addMonths(month, 1)
  return {
    key: month,
    switchProps: {
      label: monthLabel(month),
      unit: '月',
      previous: href('/records/month', { month: addMonths(month, -1) }),
      next: !hasNextMonth(month, today)
        ? null
        : next === today.slice(0, 7)
          ? href('/records/month', {})
          : href('/records/month', { month: next }),
      current: month === today.slice(0, 7) ? null : href('/records/month', {}),
      currentLabel: '今月',
    },
  }
}

interface StatsViewProps {
  kind: RecordsKind
  stats: PeriodStats
  metric: Metric
  units: Units
  onMetric: (metric: Metric) => void
}

function StatsView({ kind, stats, metric, units, onMetric }: StatsViewProps) {
  const summaryId = useId()
  const chartId = useId()
  const partsId = useId()
  const previousName = kind === 'week' ? '前の週' : '前の月'
  const chartName = kind === 'week' ? '曜日ごと' : '週ごと'
  const metricLabel = METRICS.find((m) => m.metric === metric)?.label ?? ''
  const parts = bodyPartRows(stats.bodyParts)

  return (
    <>
      <section aria-labelledby={summaryId} className={styles.card}>
        <h2 id={summaryId} className="visually-hidden">
          集計
        </h2>
        <dl className={styles.stats}>
          {summaryItems(stats.summary, stats.previous, units).map((item) => (
            <div key={item.label} className={styles.stat}>
              <dt className={styles.statLabel}>{item.label}</dt>
              <dd className={styles.statValue}>
                <span>{item.value}</span>{' '}
                <span className={`${styles.diff} ${styles[item.diff.sign]}`}>
                  <span className="visually-hidden">{previousName}との差 </span>
                  <span>{item.diff.text}</span>
                </span>
              </dd>
            </div>
          ))}
        </dl>
      </section>

      <section aria-labelledby={chartId} className={styles.card}>
        <div className={styles.cardHead}>
          <h2 id={chartId} className={styles.cardTitle}>
            {chartName}
          </h2>
          <div className={styles.segments}>
            {METRICS.map((m) => (
              <button
                key={m.metric}
                type="button"
                className={styles.segment}
                aria-pressed={m.metric === metric}
                onClick={() => onMetric(m.metric)}
              >
                {m.label}
              </button>
            ))}
          </div>
        </div>
        <StatsChart
          title={`${chartName}の${metricLabel}`}
          labelHeader={kind === 'week' ? '曜日' : '週'}
          valueName={metricLabel}
          points={stats.points(metric)}
          format={(value) => formatMetric(value, metric, units)}
        />
      </section>

      <section aria-labelledby={partsId} className={styles.card}>
        <h2 id={partsId} className={styles.cardTitle}>
          部位別のセット数
        </h2>
        {parts.length === 0 ? (
          <p className={styles.empty}>この期間の記録はありません</p>
        ) : (
          <ul className={styles.parts}>
            {parts.map((part) => (
              <li key={part.label} className={styles.part}>
                <span className={styles.partLabel}>{part.label}</span>
                <span className={styles.partBar} aria-hidden="true">
                  <span style={{ width: `${part.percent}%` }} />
                </span>
                <span className={styles.partValue}>
                  {part.setCount}セット（{part.percent}%）
                </span>
              </li>
            ))}
          </ul>
        )}
      </section>
    </>
  )
}
