import type { Exercise, ExerciseTrend, TrendPoint, TrendRange, UsedExercise } from '../api/types'
import {
  distanceValue,
  formatDistance,
  formatDuration,
  formatPaceValue,
  formatWeight,
  paceSecondsPer,
  weightValue,
} from '../format/units'
import { bodyPartLabel, type Units } from '../format/workout'
import type { ChartPoint } from './stats'

/*
 * S-22 種目ごとの推移の表示（docs/screens.md S-22、F-84・F-85）。
 * 期間は URL の `?range=3m|1y|all`、グラフの値は `?view=` に入れる。API の値は kg・km・秒で、表示するときにユーザーの単位に換算する
 */

/**
 * グラフの値。記録方式で選べる値が違う
 * - 重量 × 回数：max（最大重量）・e1rm（推定 1RM）
 * - 回数のみ：total（合計回数）・max（最大回数）
 * - 時間・距離：distance（合計距離）・time（合計時間）・pace（ペース。ランニングだけ）
 */
export type TrendView = 'max' | 'e1rm' | 'total' | 'distance' | 'time' | 'pace'

/** 部位ごとにまとめた、記録した種目 */
export interface ExerciseGroup {
  bodyPart: string
  label: string
  items: UsedExercise[]
}

const RANGES: readonly string[] = ['3m', '1y', 'all']

/** URL の `?range=`。3m・1y・all 以外・なければ 3か月 */
export function rangeFromParam(value: string | null): TrendRange {
  return value !== null && RANGES.includes(value) ? (value as TrendRange) : '3m'
}

/** その種目で選べるグラフの値（最初が既定） */
export function trendViews(exercise: Exercise): { view: TrendView; label: string }[] {
  switch (exercise.recordType) {
    case 'weight_reps':
      return [
        { view: 'max', label: '最大重量' },
        { view: 'e1rm', label: '推定 1RM' },
      ]
    case 'reps':
      return [
        { view: 'total', label: '合計回数' },
        { view: 'max', label: '最大回数' },
      ]
    default:
      return [
        { view: 'distance', label: '合計距離' },
        { view: 'time', label: '合計時間' },
        ...(exercise.isRunning ? [{ view: 'pace' as const, label: 'ペース' }] : []),
      ]
  }
}

/** URL の `?view=`。その種目で選べなければ、最初の値 */
export function viewFromParam(value: string | null, exercise: Exercise): TrendView {
  const views = trendViews(exercise)
  return views.find((v) => v.view === value)?.view ?? views[0].view
}

/** グラフの点（実施日の古い順）。値のない日は点を打たない。重量・距離・ペースは単位に換算し、時間は分にする */
export function trendPoints(trend: ExerciseTrend, view: TrendView, units: Units): ChartPoint[] {
  const valueOf = pointValue(trend.exercise, view, units)
  return trend.points.flatMap((point) => {
    const value = valueOf(point)
    return value === null ? [] : [{ label: slashed(point.date), value }]
  })
}

/** グラフ・表の値の表示（「62.5kg」「25回」「5.00km」「31分」「6:00/km」）。値は換算済み */
export function formatTrendValue(
  value: number,
  view: TrendView,
  exercise: Exercise,
  units: Units,
): string {
  if (exercise.recordType === 'weight_reps') {
    return `${value}${units.weightUnit}`
  }
  switch (view) {
    case 'distance':
      return `${value.toFixed(2)}${units.distanceUnit}`
    case 'time':
      return `${value}分`
    case 'pace':
      return formatPaceValue(value, units.distanceUnit)
    default:
      return `${value}回`
  }
}

/** 記録の一覧の行（「最大 95kg × 3回・推定 1RM 104.5kg」「合計 60回・最大 25回」「30:00・5.00km・6:00/km」） */
export function trendRow(point: TrendPoint, exercise: Exercise, units: Units): string {
  if (exercise.recordType === 'weight_reps') {
    if (point.maxWeightKg === null) {
      return '挙がったセットなし'
    }
    const max = `最大 ${formatWeight(point.maxWeightKg, units.weightUnit)} × ${point.maxWeightReps}回`
    return point.maxE1rmKg === null
      ? max
      : `${max}・推定 1RM ${formatWeight(point.maxE1rmKg, units.weightUnit)}`
  }
  if (exercise.recordType === 'reps') {
    return `合計 ${point.totalReps}回・最大 ${point.maxReps}回`
  }
  const parts = [formatDuration(point.totalDurationSec ?? 0)]
  if (point.totalDistanceKm !== null) {
    parts.push(formatDistance(point.totalDistanceKm, units.distanceUnit))
  }
  if (point.paceSecPerKm !== null) {
    parts.push(
      formatPaceValue(paceSecondsPer(point.paceSecPerKm, units.distanceUnit), units.distanceUnit),
    )
  }
  return parts.join('・')
}

/** 自己ベスト・推定 1RM の最大：「100kg（2026/10/20）」 */
export function bestLabel(kg: number, date: string, units: Units): string {
  return `${formatWeight(kg, units.weightUnit)}（${slashed(date)}）`
}

/** 記録した種目を部位ごとにまとめる（届いた順＝部位の順のまま） */
export function groupByBodyPart(used: UsedExercise[]): ExerciseGroup[] {
  const groups: ExerciseGroup[] = []
  for (const item of used) {
    const last = groups.at(-1)
    if (last?.bodyPart === item.exercise.bodyPart) {
      last.items.push(item)
    } else {
      groups.push({
        bodyPart: item.exercise.bodyPart,
        label: bodyPartLabel(item.exercise.bodyPart),
        items: [item],
      })
    }
  }
  return groups
}

/** 点から、グラフの値を取り出す関数 */
function pointValue(
  exercise: Exercise,
  view: TrendView,
  units: Units,
): (point: TrendPoint) => number | null {
  const weight = (kg: number | null) =>
    kg === null ? null : Number(weightValue(kg, units.weightUnit))
  if (exercise.recordType === 'weight_reps') {
    return view === 'e1rm' ? (p) => weight(p.maxE1rmKg) : (p) => weight(p.maxWeightKg)
  }
  switch (view) {
    case 'total':
      return (p) => p.totalReps
    case 'max':
      return (p) => p.maxReps
    case 'distance':
      return (p) =>
        p.totalDistanceKm === null
          ? null
          : Number(distanceValue(p.totalDistanceKm, units.distanceUnit))
    case 'time':
      return (p) => (p.totalDurationSec === null ? null : Math.round(p.totalDurationSec / 60))
    default:
      return (p) =>
        p.paceSecPerKm === null ? null : paceSecondsPer(p.paceSecPerKm, units.distanceUnit)
  }
}

/** 「2026/11/14」 */
function slashed(date: string): string {
  return date.replaceAll('-', '/')
}
