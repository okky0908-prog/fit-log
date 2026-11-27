import { describe, expect, test } from 'vitest'
import { toBodyRecordInput, validateBodyRecord } from './body'

// 体重の記録の入力（docs/features.md「体重の記録」、docs/test-plan.md の境界値）。決まりと文言はサーバー（BodyRecordRules）と同じ

const TODAY = '2026-11-14'

function check(weight: string, unit: 'kg' | 'lb' = 'kg', bodyFatPct = '', recordedOn = TODAY) {
  return validateBodyRecord({ recordedOn, weight, bodyFatPct }, unit, TODAY)
}

describe('体重（kg）', () => {
  test.each(['20.0', '20', '73.1', '300.0', ' 73.0 '])('%s は通る', (weight) => {
    expect(check(weight)).toEqual({})
  })

  test.each(['19.9', '300.1', '73.05', '', 'abc', '-73', '7e1'])('%s は通らない', (weight) => {
    expect(check(weight)).toEqual({
      weight: '体重は20.0〜300.0kgの範囲で、0.1kg刻みで入力してください',
    })
  })
})

describe('体重（lb）', () => {
  test.each(['44.0', '150.2', '661.0', '150'])('%s は通る', (weight) => {
    expect(check(weight, 'lb')).toEqual({})
  })

  test.each(['43.8', '661.2', '150.1'])('%s は通らない', (weight) => {
    expect(check(weight, 'lb')).toEqual({
      weight: '体重は44.0〜661.0lbの範囲で、0.2lb刻みで入力してください',
    })
  })
})

describe('体脂肪率（任意）', () => {
  test.each(['', '1.0', '18.5', '75.0'])('「%s」は通る', (pct) => {
    expect(check('73.0', 'kg', pct)).toEqual({})
  })

  test.each(['0.9', '75.1', '18.55', 'abc'])('%s は通らない', (pct) => {
    expect(check('73.0', 'kg', pct)).toEqual({
      bodyFatPct: '体脂肪率は1.0〜75.0%の範囲で、0.1%刻みで入力してください',
    })
  })
})

describe('日付', () => {
  test('今日は通り、未来・空は通らない。すべての項目のエラーを集める', () => {
    expect(check('73.0', 'kg', '', '2026-11-14')).toEqual({})
    expect(check('73.0', 'kg', '', '2026-11-15')).toEqual({ recordedOn: '未来の日付は選べません' })
    expect(check('19.9', 'kg', '0.9', '')).toEqual({
      recordedOn: '日付を入力してください',
      weight: '体重は20.0〜300.0kgの範囲で、0.1kg刻みで入力してください',
      bodyFatPct: '体脂肪率は1.0〜75.0%の範囲で、0.1%刻みで入力してください',
    })
  })
})

describe('送る値', () => {
  test('数にし、体脂肪率が空なら null', () => {
    expect(
      toBodyRecordInput({ recordedOn: TODAY, weight: ' 73.0 ', bodyFatPct: '' }, 'kg'),
    ).toEqual({ weight: 73, weightUnit: 'kg', bodyFatPct: null })
    expect(
      toBodyRecordInput({ recordedOn: TODAY, weight: '150.2', bodyFatPct: '18.5' }, 'lb'),
    ).toEqual({ weight: 150.2, weightUnit: 'lb', bodyFatPct: 18.5 })
  })
})
