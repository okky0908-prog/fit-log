import type { BodyPartCount, MonthlyStats, StatsSummary, WeeklyStats } from '../api/types'
import { distanceValue, KG_PER_LB } from '../format/units'
import { bodyPartLabel, type Units } from '../format/workout'

/*
 * S-20 の集計値・前の期間との差・グラフの点・部位別のセット数の表示（docs/screens.md S-20）。
 * API の値は kg・km・秒。総挙上量はユーザーの重量の単位で整数に、運動時間は分に、距離はユーザーの距離の単位で小数第2位まで出す
 */

/** グラフに出す値（総挙上量か運動時間） */
export type Metric = 'volume' | 'duration'

/** 前の期間との差。記号（＋・−・±）を文字に入れ、色は sign で付ける */
export interface Diff {
  text: string
  sign: 'up' | 'down' | 'same'
}

export interface SummaryItem {
  label: string
  value: string
  diff: Diff
}

export interface ChartPoint {
  label: string
  value: number
}

export interface BodyPartRow {
  label: string
  setCount: number
  percent: number
}

const WEEKDAYS = ['月', '火', '水', '木', '金', '土', '日']

/** 集計値（トレーニング日数・総挙上量・運動時間・距離）と、それぞれの前の期間との差（表示の桁に丸めた値どうしで引く） */
export function summaryItems(
  summary: StatsSummary,
  previous: StatsSummary,
  units: Units,
): SummaryItem[] {
  const volume = volumeIn(summary.totalVolumeKg, units)
  const minutes = toMinutes(summary.totalDurationSec)
  const distance = distanceValue(summary.totalDistanceKm, units.distanceUnit)
  return [
    {
      label: 'トレーニング日数',
      value: `${summary.trainingDays}日`,
      diff: formatDiff(summary.trainingDays, previous.trainingDays),
    },
    {
      label: '総挙上量',
      value: formatMetric(volume, 'volume', units),
      diff: formatDiff(volume, volumeIn(previous.totalVolumeKg, units)),
    },
    {
      label: '運動時間',
      value: formatMetric(minutes, 'duration', units),
      diff: formatDiff(minutes, toMinutes(previous.totalDurationSec)),
    },
    {
      label: '距離',
      value: `${distance}${units.distanceUnit}`,
      diff: formatDiff(
        Number(distance),
        Number(distanceValue(previous.totalDistanceKm, units.distanceUnit)),
        2,
      ),
    },
  ]
}

/** 前の期間との差。「+800」「−10」「±0」（3桁ごとに区切り、小数は digits 桁） */
export function formatDiff(current: number, previous: number, digits = 0): Diff {
  const diff = Number((current - previous).toFixed(digits))
  if (diff === 0) {
    return { text: '±0', sign: 'same' }
  }
  const text = Math.abs(diff).toLocaleString('ja-JP', {
    minimumFractionDigits: digits,
    maximumFractionDigits: digits,
  })
  return diff > 0 ? { text: `+${text}`, sign: 'up' } : { text: `−${text}`, sign: 'down' }
}

/** 週間のグラフ：曜日ごと（月〜日） */
export function dailyPoints(stats: WeeklyStats, metric: Metric, units: Units): ChartPoint[] {
  return stats.daily.map((day, index) => ({
    label: WEEKDAYS[index],
    value: metric === 'volume' ? volumeIn(day.volumeKg, units) : toMinutes(day.durationSec),
  }))
}

/** 月間のグラフ：週ごと。月をまたぐ週は、その月の最初の日から（「11/1〜」） */
export function weeklyPoints(stats: MonthlyStats, metric: Metric, units: Units): ChartPoint[] {
  return stats.weekly.map((week) => {
    const start = week.weekStart < stats.from ? stats.from : week.weekStart
    return {
      label: `${Number(start.slice(5, 7))}/${Number(start.slice(8, 10))}〜`,
      value: metric === 'volume' ? volumeIn(week.volumeKg, units) : toMinutes(week.durationSec),
    }
  })
}

/** グラフの値の表示（「12,450kg」「95分」）。値は換算済み */
export function formatMetric(value: number, metric: Metric, units: Units): string {
  return metric === 'volume'
    ? `${value.toLocaleString('ja-JP')}${units.weightUnit}`
    : `${value.toLocaleString('ja-JP')}分`
}

/**
 * グラフの横軸の目盛りの文字。スマートフォンの幅でも全部出るよう、週の「〜」と、月の「月」（「11月」→「11」）、日付の年（「2026/11/04」→「11/4」）を省く。
 * 曜日の「月」（月曜）は数字が付かないので省かない
 */
export function axisLabel(label: string): string {
  const date = /^\d{4}\/(\d{2})\/(\d{2})$/.exec(label)
  if (date !== null) {
    return `${Number(date[1])}/${Number(date[2])}`
  }
  return label.replace('〜', '').replace(/^(\d+)月$/, '$1')
}

/** グラフに出す値があるか（すべて 0 なら「この期間の記録はありません」） */
export function hasChartData(points: ChartPoint[]): boolean {
  return points.some((point) => point.value > 0)
}

/** 部位別のセット数。部位の名前と割合（%。四捨五入）を、届いた順（多い順）に */
export function bodyPartRows(parts: BodyPartCount[]): BodyPartRow[] {
  const total = parts.reduce((sum, part) => sum + part.setCount, 0)
  return parts.map((part) => ({
    label: bodyPartLabel(part.bodyPart),
    setCount: part.setCount,
    percent: Math.round((part.setCount / total) * 100),
  }))
}

/** 総挙上量を重量の単位に換算し、整数に丸める */
function volumeIn(kg: number, units: Units): number {
  return Math.round(units.weightUnit === 'lb' ? kg / KG_PER_LB : kg)
}

function toMinutes(seconds: number): number {
  return Math.round(seconds / 60)
}
