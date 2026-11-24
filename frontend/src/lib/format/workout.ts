import type { ExerciseSummary } from '../api/types'
import {
  type DistanceUnit,
  formatDistance,
  formatDuration,
  formatPace,
  formatWeight,
  type WeightUnit,
} from './units'

/** セットの値（API の Workout の形。kg・km・秒） */
export interface SetValues {
  weightKg: number | null
  reps: number | null
  durationSec: number | null
  distanceKm: number | null
}

/** ユーザーが選んだ単位（docs/features.md F-63） */
export interface Units {
  weightUnit: WeightUnit
  distanceUnit: DistanceUnit
}

/**
 * 投稿詳細のセットの行（docs/screens.md S-12）。記録方式で出す値が変わる。並べて表示するので、値ごとに分けて返す
 * - 重量 × 回数：「60kg × 10回」
 * - 回数のみ：「20回」
 * - 時間・距離：「5.00km」「30:00」と、ランニングならペース「6:00/km」。距離がなければ時間だけ
 */
export function formatSet(
  set: SetValues,
  recordType: string,
  isRunning: boolean,
  units: Units,
): string[] {
  // API は、記録方式に合う値を必ず返す（DB の CHECK でも止めている）ので、ここでは null を見ない
  if (recordType === 'weight_reps') {
    return [`${formatWeight(set.weightKg as number, units.weightUnit)} × ${set.reps}回`]
  }
  if (recordType === 'reps') {
    return [`${set.reps}回`]
  }
  const duration = set.durationSec as number
  if (set.distanceKm === null) {
    return [formatDuration(duration)]
  }
  const parts = [formatDistance(set.distanceKm, units.distanceUnit), formatDuration(duration)]
  // ペースは、走った速さとして意味のあるランニングだけに出す（F-83・UC-11）
  return isRunning ? [...parts, formatPace(duration, set.distanceKm, units.distanceUnit)] : parts
}

/**
 * 投稿カードの種目の要約の値（種目の名前は除く。docs/features.md「投稿カードに表示する内容」）
 * - 重量 × 回数：「60kg × 10回 × 3セット」／値が違えば「3セット（最大 100kg × 5回）」
 * - 回数のみ：「20回 × 3セット」／値が違えば「合計 55回（3セット）」
 * - 時間・距離：1回なら「5.00km 30:00（6:00/km）」、2回以上なら「合計 8.00km 50:00（2回）」。距離がなければ時間だけ
 */
export function formatExerciseSummary(summary: ExerciseSummary, units: Units): string {
  const sets = `${summary.setCount}セット`
  // API は、記録方式に合う値を必ず返すので、ここでは null を見ない
  if (summary.recordType === 'weight_reps') {
    const best = `${formatWeight(summary.weightKg as number, units.weightUnit)} × ${summary.reps}回`
    return summary.uniform ? `${best} × ${sets}` : `${sets}（最大 ${best}）`
  }
  if (summary.recordType === 'reps') {
    return summary.uniform
      ? `${summary.reps}回 × ${sets}`
      : `合計 ${summary.totalReps}回（${sets}）`
  }
  const duration = summary.durationSec as number
  const values = [
    ...(summary.distanceKm === null
      ? []
      : [formatDistance(summary.distanceKm, units.distanceUnit)]),
    formatDuration(duration),
  ].join(' ')
  if (summary.setCount > 1) {
    return `合計 ${values}（${summary.setCount}回）`
  }
  return summary.distanceKm === null
    ? values
    : `${values}（${formatPace(duration, summary.distanceKm, units.distanceUnit)}）`
}

const BODY_PARTS: Record<string, string> = {
  chest: '胸',
  back: '背中',
  legs: '脚',
  shoulders: '肩',
  arms: '腕',
  abs: '腹',
  full_body: '全身',
  cardio: '有酸素',
}

/** 部位の名前。知らない値（API に部位が増えたとき）はそのまま出す */
export function bodyPartLabel(bodyPart: string): string {
  return BODY_PARTS[bodyPart] ?? bodyPart
}
