import type { BodyRecordInput } from '../api/types'

/*
 * 体重の記録の入力（docs/features.md「体重の記録」、docs/screens.md S-25）。決まりと文言はサーバー（BodyRecordRules）と同じ。
 * 範囲と刻みは入力した単位で確かめる（kg は 20.0〜300.0 の 0.1 刻み、lb は 44.0〜661.0 の 0.2 刻み、体脂肪率は 1.0〜75.0 の 0.1 刻み）。
 * 2進数の誤差で刻みの判定がずれないよう、小数第1位までの数として読み、十分の一の整数で比べる
 */

/** 入力欄の値（文字のまま） */
export interface BodyForm {
  recordedOn: string
  weight: string
  bodyFatPct: string
}

export type BodyErrors = Partial<Record<'recordedOn' | 'weight' | 'bodyFatPct', string>>

const KG_WEIGHT = '体重は20.0〜300.0kgの範囲で、0.1kg刻みで入力してください'
const LB_WEIGHT = '体重は44.0〜661.0lbの範囲で、0.2lb刻みで入力してください'
const BODY_FAT = '体脂肪率は1.0〜75.0%の範囲で、0.1%刻みで入力してください'

/** 範囲（十分の一の整数）と刻み（十分の一の整数） */
const WEIGHT_RULES = {
  kg: { min: 200, max: 3000, step: 1, message: KG_WEIGHT },
  lb: { min: 440, max: 6610, step: 2, message: LB_WEIGHT },
} as const

/** すべての項目のエラーを集める */
export function validateBodyRecord(form: BodyForm, unit: 'kg' | 'lb', today: string): BodyErrors {
  const errors: BodyErrors = {}
  if (form.recordedOn === '') {
    errors.recordedOn = '日付を入力してください'
  } else if (form.recordedOn > today) {
    errors.recordedOn = '未来の日付は選べません'
  }
  const rule = WEIGHT_RULES[unit]
  if (!inSteps(tenths(form.weight), rule.min, rule.max, rule.step)) {
    errors.weight = rule.message
  }
  if (form.bodyFatPct.trim() !== '' && !inSteps(tenths(form.bodyFatPct), 10, 750, 1)) {
    errors.bodyFatPct = BODY_FAT
  }
  return errors
}

/** validateBodyRecord を通った入力を、A-100 に送る形にする（体脂肪率が空なら null） */
export function toBodyRecordInput(form: BodyForm, unit: 'kg' | 'lb'): BodyRecordInput {
  const fat = form.bodyFatPct.trim()
  return {
    weight: Number(form.weight.trim()),
    weightUnit: unit,
    bodyFatPct: fat === '' ? null : Number(fat),
  }
}

/** 「73.1」→ 731。小数第1位までの数でなければ null */
function tenths(value: string): number | null {
  const match = /^(\d+)(?:\.(\d))?$/.exec(value.trim())
  return match === null ? null : Number(match[1]) * 10 + Number(match[2] ?? 0)
}

function inSteps(value: number | null, min: number, max: number, step: number): boolean {
  return value !== null && value >= min && value <= max && value % step === 0
}
