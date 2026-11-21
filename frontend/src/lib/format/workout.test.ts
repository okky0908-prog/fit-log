import { describe, expect, test } from 'vitest'
import { bodyPartLabel, formatSet } from './workout'

// 投稿詳細のセットの行（docs/screens.md S-12）

const KG_KM = { weightUnit: 'kg', distanceUnit: 'km' } as const
const LB_MI = { weightUnit: 'lb', distanceUnit: 'mi' } as const

const set = (values: Partial<Parameters<typeof formatSet>[0]>) => ({
  weightKg: null,
  reps: null,
  durationSec: null,
  distanceKm: null,
  ...values,
})

describe('セットの表示', () => {
  test('重量 × 回数', () => {
    expect(formatSet(set({ weightKg: 60, reps: 10 }), 'weight_reps', false, KG_KM)).toEqual([
      '60kg × 10回',
    ])
    expect(formatSet(set({ weightKg: 61.235, reps: 8 }), 'weight_reps', false, LB_MI)).toEqual([
      '135lb × 8回',
    ])
    // 挙がらなかったセット（0回）
    expect(formatSet(set({ weightKg: 100, reps: 0 }), 'weight_reps', false, KG_KM)).toEqual([
      '100kg × 0回',
    ])
  })

  test('回数のみ', () => {
    expect(formatSet(set({ reps: 20 }), 'reps', false, KG_KM)).toEqual(['20回'])
  })

  test('ランニングは距離・時間・ペース', () => {
    expect(
      formatSet(set({ durationSec: 1800, distanceKm: 5 }), 'time_distance', true, KG_KM),
    ).toEqual(['5.00km', '30:00', '6:00/km'])
    expect(
      formatSet(set({ durationSec: 1800, distanceKm: 4.989 }), 'time_distance', true, LB_MI),
    ).toEqual(['3.10mi', '30:00', '9:41/mi'])
  })

  test('ランニング以外の時間・距離の種目は_ペースを出さない', () => {
    expect(
      formatSet(set({ durationSec: 3600, distanceKm: 20 }), 'time_distance', false, KG_KM),
    ).toEqual(['20.00km', '1:00:00'])
  })

  test('距離がなければ時間だけ', () => {
    expect(formatSet(set({ durationSec: 600 }), 'time_distance', true, KG_KM)).toEqual(['10:00'])
  })
})

describe('部位の名前', () => {
  test('8つの部位', () => {
    expect(
      ['chest', 'back', 'legs', 'shoulders', 'arms', 'abs', 'full_body', 'cardio'].map(
        bodyPartLabel,
      ),
    ).toEqual(['胸', '背中', '脚', '肩', '腕', '腹', '全身', '有酸素'])
  })

  test('知らない値はそのまま', () => {
    expect(bodyPartLabel('unknown')).toBe('unknown')
  })
})
