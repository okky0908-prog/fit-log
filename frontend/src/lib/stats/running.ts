import type { BestRun, RunningStats, RunningSummary } from '../api/types'
import {
  distanceValue,
  type DistanceUnit,
  formatDistance,
  formatDuration,
  formatPace,
  formatPaceValue,
  paceSecondsPer,
} from '../format/units'
import { addDays, addMonths, isDate, monthLabel, weekLabel, weekStartOf } from './period'
import { type ChartPoint, type Diff, formatDiff } from './stats'

/*
 * S-21 ランニングの記録の期間と表示（docs/screens.md S-21、F-83）。
 * 期間は URL の `?period=week|month|year&date=` で表し、画面の中では期間の始まりの日（週は月曜、月は1日、年は1月1日）で持つ。
 * API の値は km・秒。距離・ペースはユーザーの距離の単位に換算して出す
 */

export type RunningUnit = 'week' | 'month' | 'year'

export interface RunningPeriod {
  unit: RunningUnit
  /** 期間の始まりの日（YYYY-MM-DD） */
  start: string
}

/** 集計値の1つ。差を出せない（平均ペースがない）ときは diff が null */
export interface RunningItem {
  label: string
  value: string
  diff: Diff | null
}

/** ベスト記録の表示（「5:12/km」と「10.00km・52:00・11/8」） */
export interface BestRunText {
  value: string
  detail: string
}

const UNITS: readonly string[] = ['week', 'month', 'year']

/** その日を含む期間の始まりの日 */
export function runningStart(unit: RunningUnit, date: string): string {
  if (unit === 'week') {
    return weekStartOf(date)
  }
  return unit === 'month' ? `${date.slice(0, 7)}-01` : `${date.slice(0, 4)}-01-01`
}

/**
 * URL の `?period=`・`?date=` から、表示する期間。period がなければ・知らない値なら月間。
 * date がなければ・正しい日付でなければ・未来の期間なら、今日を含む期間
 */
export function runningPeriodFromParams(
  period: string | null,
  date: string | null,
  today: string,
): RunningPeriod {
  const unit = period !== null && UNITS.includes(period) ? (period as RunningUnit) : 'month'
  const current = runningStart(unit, today)
  if (date === null || !isDate(date)) {
    return { unit, start: current }
  }
  const start = runningStart(unit, date)
  return { unit, start: start > current ? current : start }
}

/** count 個先（負の数なら前）の期間 */
export function shiftRunning(period: RunningPeriod, count: number): RunningPeriod {
  const { unit, start } = period
  if (unit === 'week') {
    return { unit, start: addDays(start, count * 7) }
  }
  return {
    unit,
    start: `${addMonths(start.slice(0, 7), unit === 'month' ? count : count * 12)}-01`,
  }
}

/** 次の期間へ移れるか（次の期間の始まりの日が今日以前） */
export function hasNextRunning(period: RunningPeriod, today: string): boolean {
  return shiftRunning(period, 1).start <= today
}

/** 「2026/11/09（月）〜 11/15（日）」「2026年11月」「2026年」 */
export function runningLabel(period: RunningPeriod): string {
  if (period.unit === 'week') {
    return weekLabel(period.start)
  }
  return period.unit === 'month'
    ? monthLabel(period.start.slice(0, 7))
    : `${period.start.slice(0, 4)}年`
}

/** 走行距離・走行時間・回数・平均ペースと、前の期間との差（表示の桁に丸めた値どうしで引く） */
export function runningSummaryItems(
  summary: RunningSummary,
  previous: RunningSummary,
  unit: DistanceUnit,
): RunningItem[] {
  const pace = summary.avgPaceSecPerKm
  const previousPace = previous.avgPaceSecPerKm
  return [
    {
      label: '走行距離',
      value: formatDistance(summary.distanceKm, unit),
      diff: formatDiff(
        Number(distanceValue(summary.distanceKm, unit)),
        Number(distanceValue(previous.distanceKm, unit)),
        2,
      ),
    },
    {
      label: '走行時間',
      value: formatDuration(summary.durationSec),
      diff: durationDiff(summary.durationSec - previous.durationSec, false),
    },
    {
      label: '回数',
      value: `${summary.runCount}回`,
      diff: formatDiff(summary.runCount, previous.runCount),
    },
    {
      label: '平均ペース',
      value: pace === null ? '—' : formatPaceValue(paceSecondsPer(pace, unit), unit),
      diff:
        pace === null || previousPace === null
          ? null
          : durationDiff(paceSecondsPer(pace, unit) - paceSecondsPer(previousPace, unit), true),
    },
  ]
}

/** 最速ペースの1回：「5:12/km」と「10.00km・52:00・11/8」 */
export function bestPaceText(run: BestRun, unit: DistanceUnit): BestRunText {
  return {
    value: formatPace(run.durationSec, run.distanceKm, unit),
    detail: `${formatDistance(run.distanceKm, unit)}・${formatDuration(run.durationSec)}・${monthDay(run.performedOn)}`,
  }
}

/** 最長距離の1回：「15.00km」と「1:35:20・11/3」 */
export function longestRunText(run: BestRun, unit: DistanceUnit): BestRunText {
  return {
    value: formatDistance(run.distanceKm, unit),
    detail: `${formatDuration(run.durationSec)}・${monthDay(run.performedOn)}`,
  }
}

/** 年間のグラフ：月ごとの走行距離（「1月」〜「12月」。値は距離の単位に換算し、小数第2位まで） */
export function monthlyDistancePoints(
  monthly: RunningStats['monthly'],
  unit: DistanceUnit,
): ChartPoint[] {
  return monthly.map((month) => ({
    label: `${Number(month.month.slice(5, 7))}月`,
    value: Number(distanceValue(month.distanceKm, unit)),
  }))
}

/**
 * 時間の差（「+25:00」「−1:01:00」「±0」）。ペース（lowerIsBetter）は秒が減る（速くなる）ほどよいので、
 * − をよい方の色（up）、＋ をよくない方の色（down）にする
 */
function durationDiff(seconds: number, lowerIsBetter: boolean): Diff {
  if (seconds === 0) {
    return { text: '±0', sign: 'same' }
  }
  const text = formatDuration(Math.abs(seconds))
  const better = lowerIsBetter ? seconds < 0 : seconds > 0
  return { text: seconds > 0 ? `+${text}` : `−${text}`, sign: better ? 'up' : 'down' }
}

/** 「11/8」 */
function monthDay(date: string): string {
  return `${Number(date.slice(5, 7))}/${Number(date.slice(8, 10))}`
}
