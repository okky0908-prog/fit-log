/*
 * 重量・距離・時間・ペースの表示（docs/features.md F-63）。
 * - 保存・API の値は kg・km・秒。表示するときに、ユーザーが選んだ単位に換算する（1lb ＝ 0.45359237kg、1マイル ＝ 1.609344km）
 * - 表示の桁：重量は kg なら 0.25・lb なら 0.5 単位に丸める（入力した値はそのまま戻る）。距離は小数第2位まで。ペースは秒単位（m:ss）
 */

export type WeightUnit = 'kg' | 'lb'
export type DistanceUnit = 'km' | 'mi'

export const KG_PER_LB = 0.45359237
export const KM_PER_MILE = 1.609344

/** 重量の丸めの単位（kg は 0.25、lb は 0.5） */
const WEIGHT_STEP: Record<WeightUnit, number> = { kg: 0.25, lb: 0.5 }

/**
 * 四捨五入。1.005 のように、割ると 2進数で少し小さく表される値（100.49999…）が切り捨てられないよう、ごく小さな値を足してから丸める。
 * 表示に使う値は正の数だけ
 */
function roundHalfUp(value: number, step: number): number {
  return Math.round(value / step + 1e-9) * step
}

/** 重量を単位に換算し、表示の桁に丸めた値（「61.25」「135」。余分な 0 は付けない）。編集の入力欄の初期値にも使う */
export function weightValue(kg: number, unit: WeightUnit): string {
  const value = unit === 'lb' ? kg / KG_PER_LB : kg
  // 丸めたあとの 2進数の誤差（60.250000001 など）を消してから文字にする
  return String(Number(roundHalfUp(value, WEIGHT_STEP[unit]).toFixed(2)))
}

/** 「60kg」「62.5kg」「135lb」の形 */
export function formatWeight(kg: number, unit: WeightUnit): string {
  return `${weightValue(kg, unit)}${unit}`
}

/** 距離を単位に換算し、小数第2位までにした値（「5.00」「3.10」）。編集の入力欄の初期値にも使う */
export function distanceValue(km: number, unit: DistanceUnit): string {
  const value = unit === 'mi' ? km / KM_PER_MILE : km
  return roundHalfUp(value, 0.01).toFixed(2)
}

/** 「5.00km」「3.10mi」の形 */
export function formatDistance(km: number, unit: DistanceUnit): string {
  return `${distanceValue(km, unit)}${unit}`
}

/** 1時間未満は「30:00」、1時間以上は「1:05:05」の形 */
export function formatDuration(seconds: number): string {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const rest = seconds % 60
  return hours > 0
    ? `${hours}:${twoDigits(minutes)}:${twoDigits(rest)}`
    : `${minutes}:${twoDigits(rest)}`
}

/** 「6:00/km」「8:00/mi」の形。1km（1マイル）あたりの秒を四捨五入し、分は 60 を超えてもそのまま出す */
export function formatPace(seconds: number, km: number, unit: DistanceUnit): string {
  const distance = unit === 'mi' ? km / KM_PER_MILE : km
  const perUnit = Math.round(seconds / distance)
  return `${Math.floor(perUnit / 60)}:${twoDigits(perUnit % 60)}/${unit}`
}

/** 集計のペース（1km あたりの秒）を、距離の単位あたりの秒（整数に四捨五入）に */
export function paceSecondsPer(secondsPerKm: number, unit: DistanceUnit): number {
  return unit === 'mi' ? Math.round(secondsPerKm * KM_PER_MILE) : secondsPerKm
}

/** 距離の単位あたりの秒を「6:00/km」の形に。formatPace と同じく、分は 60 を超えてもそのまま出す */
export function formatPaceValue(secondsPerUnit: number, unit: DistanceUnit): string {
  return `${Math.floor(secondsPerUnit / 60)}:${twoDigits(secondsPerUnit % 60)}/${unit}`
}

function twoDigits(value: number): string {
  return String(value).padStart(2, '0')
}
