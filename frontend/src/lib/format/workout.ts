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
