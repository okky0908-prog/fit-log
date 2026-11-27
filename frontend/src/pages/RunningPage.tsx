import { useQuery } from '@tanstack/react-query'
import { useId, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { BackButton } from '../components/BackButton'
import { PeriodSwitch } from '../components/PeriodSwitch'
import { StatsChart } from '../components/StatsChart'
import { errorMessage, fetchRunningStats } from '../lib/api/client'
import type { BestRun, RunningStats } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { tokyoDate } from '../lib/format/date'
import type { DistanceUnit } from '../lib/format/units'
import {
  type BestRunText,
  bestPaceText,
  hasNextRunning,
  longestRunText,
  monthlyDistancePoints,
  type RunningPeriod,
  type RunningUnit,
  runningLabel,
  runningPeriodFromParams,
  runningStart,
  runningSummaryItems,
  shiftRunning,
} from '../lib/stats/running'
import styles from './RecordsPage.module.css'

const TABS: { unit: RunningUnit; label: string; name: string; current: string }[] = [
  { unit: 'week', label: '週間', name: '週', current: '今週' },
  { unit: 'month', label: '月間', name: '月', current: '今月' },
  { unit: 'year', label: '年間', name: '年', current: '今年' },
]

/**
 * S-21 ランニングの記録（/records/running）（docs/screens.md S-21、F-83）。
 * 期間は URL の `?period=week|month|year&date=` に入れる。今の期間を表示するときは date を入れない
 */
export function RunningPage() {
  const { user } = useAuth()
  const distanceUnit: DistanceUnit = user?.distanceUnit ?? 'km'
  const [searchParams] = useSearchParams()
  // 「今日」（日本時間）。描き直すたびに変わらないよう、開いたときの日付を持つ
  const [today] = useState(() => tokyoDate(new Date()))
  const period = runningPeriodFromParams(
    searchParams.get('period'),
    searchParams.get('date'),
    today,
  )
  const tab = TABS.find((t) => t.unit === period.unit) ?? TABS[1]

  const href = (target: RunningPeriod) => {
    const params = new URLSearchParams({ period: target.unit })
    if (target.start !== runningStart(target.unit, today)) params.set('date', target.start)
    return `/records/running?${params}`
  }
  // 今の期間を見ているなら、切り替えた先も今の期間。それ以外は、見ている期間の始まりの日を含む期間
  const viewing = period.start === runningStart(period.unit, today) ? today : period.start
  const isCurrent = viewing === today

  const stats = useQuery({
    queryKey: ['stats', 'running', period.unit, period.start],
    queryFn: () => fetchRunningStats(period.unit, period.start),
  })

  return (
    <>
      <BackButton fallback="/records">記録</BackButton>
      <h1 className={styles.title}>ランニング</h1>
      <div role="tablist" className={styles.tabs}>
        {TABS.map((t) => (
          <Link
            key={t.unit}
            to={href({ unit: t.unit, start: runningStart(t.unit, viewing) })}
            replace
            role="tab"
            aria-selected={t.unit === period.unit}
            className={styles.tab}
          >
            {t.label}
          </Link>
        ))}
      </div>
      <div role="tabpanel" aria-label={tab.label}>
        <PeriodSwitch
          label={runningLabel(period)}
          unit={tab.name}
          previous={href(shiftRunning(period, -1))}
          next={hasNextRunning(period, today) ? href(shiftRunning(period, 1)) : null}
          current={
            isCurrent ? null : href({ unit: period.unit, start: runningStart(period.unit, today) })
          }
          currentLabel={tab.current}
        />
        {stats.error ? (
          <div className={styles.message}>
            <p>{errorMessage(stats.error)}</p>
            <button type="button" className="button-secondary" onClick={() => void stats.refetch()}>
              もう一度読み込む
            </button>
          </div>
        ) : !stats.data ? (
          <p className={styles.message}>読み込み中…</p>
        ) : stats.data.summary.runCount === 0 ? (
          <p className={styles.message}>この期間のランニングの記録はありません</p>
        ) : (
          <RunningView
            stats={stats.data}
            unit={period.unit}
            previousName={`前の${tab.name}`}
            distanceUnit={distanceUnit}
          />
        )}
      </div>
    </>
  )
}

interface RunningViewProps {
  stats: RunningStats
  unit: RunningUnit
  previousName: string
  distanceUnit: DistanceUnit
}

function RunningView({ stats, unit, previousName, distanceUnit }: RunningViewProps) {
  const summaryId = useId()
  const bestId = useId()
  const chartId = useId()
  return (
    <>
      <section aria-labelledby={summaryId} className={styles.card}>
        <h2 id={summaryId} className="visually-hidden">
          集計
        </h2>
        <dl className={styles.stats}>
          {runningSummaryItems(stats.summary, stats.previous, distanceUnit).map((item) => (
            <div key={item.label} className={styles.stat}>
              <dt className={styles.statLabel}>{item.label}</dt>
              <dd className={styles.statValue}>
                <span>{item.value}</span>
                {item.diff !== null && (
                  <>
                    {' '}
                    <span className={`${styles.diff} ${styles[item.diff.sign]}`}>
                      <span className="visually-hidden">{previousName}との差 </span>
                      <span>{item.diff.text}</span>
                    </span>
                  </>
                )}
              </dd>
            </div>
          ))}
        </dl>
      </section>

      <section aria-labelledby={bestId} className={styles.card}>
        <h2 id={bestId} className={styles.cardTitle}>
          ベスト記録
        </h2>
        <ul className={styles.bests}>
          <li>
            <BestRunLine
              icon="⚡"
              name="最速ペース"
              run={stats.bestPace}
              text={(run) => bestPaceText(run, distanceUnit)}
            />
            <p className={styles.note}>（1km 以上の記録から）</p>
          </li>
          <li>
            <BestRunLine
              icon="📏"
              name="最長距離"
              run={stats.longestRun}
              text={(run) => longestRunText(run, distanceUnit)}
            />
          </li>
        </ul>
      </section>

      {unit === 'year' && (
        <section aria-labelledby={chartId} className={styles.card}>
          <h2 id={chartId} className={styles.cardTitle}>
            月ごとの走行距離
          </h2>
          <StatsChart
            title="月ごとの走行距離"
            labelHeader="月"
            valueName="走行距離"
            points={monthlyDistancePoints(stats.monthly, distanceUnit)}
            format={(value) => `${value.toFixed(2)}${distanceUnit}`}
          />
        </section>
      )}
    </>
  )
}

interface BestRunLineProps {
  icon: string
  name: string
  run: BestRun | null
  text: (run: BestRun) => BestRunText
}

/** ベスト記録の1行。押すとその記録の詳細（S-12）へ。対象の記録がなければ「—」 */
function BestRunLine({ icon, name, run, text }: BestRunLineProps) {
  if (run === null) {
    return (
      <p className={styles.best}>
        <span aria-hidden="true">{icon} </span>
        <span>{name}</span> <span>—</span>
      </p>
    )
  }
  const { value, detail } = text(run)
  return (
    <Link to={`/workouts/${run.workoutId}`} className={`${styles.best} ${styles.bestLink}`}>
      <span aria-hidden="true">{icon} </span>
      <span>{name}</span> <strong className={styles.bestValue}>{value}</strong>
      <span className={styles.bestDetail}>（{detail}）</span>
      <span aria-hidden="true" className={styles.chevron}>
        ›
      </span>
    </Link>
  )
}
