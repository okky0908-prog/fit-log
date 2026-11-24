import { describe, expect, test } from 'vitest'
import type { ExerciseSummary } from '../api/types'
import { bodyPartLabel, formatExerciseSummary, formatSet } from './workout'

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

// 投稿カードの種目の要約（docs/features.md「投稿カードに表示する内容」、docs/test-plan.md 5.4）

const summary = (values: Partial<ExerciseSummary>): ExerciseSummary => ({
  exerciseId: 1,
  name: 'ベンチプレス',
  recordType: 'weight_reps',
  personalBest: false,
  setCount: 3,
  uniform: true,
  weightKg: null,
  reps: null,
  totalReps: null,
  durationSec: null,
  distanceKm: null,
  paceSecPerKm: null,
  ...values,
})

describe('種目の要約', () => {
  test('重量 × 回数：全セットが同じなら値とセット数、違えば最大のセット', () => {
    expect(formatExerciseSummary(summary({ weightKg: 60, reps: 10 }), KG_KM)).toBe(
      '60kg × 10回 × 3セット',
    )
    expect(formatExerciseSummary(summary({ uniform: false, weightKg: 100, reps: 5 }), KG_KM)).toBe(
      '3セット（最大 100kg × 5回）',
    )
    // lb で見る人には lb で出す（61.235kg ＝ 135lb）
    expect(formatExerciseSummary(summary({ weightKg: 61.235, reps: 8, setCount: 1 }), LB_MI)).toBe(
      '135lb × 8回 × 1セット',
    )
  })

  test('回数のみ：全セットが同じなら回数とセット数、違えば合計', () => {
    const pushUp = { recordType: 'reps', name: '腕立て伏せ' } as const
    expect(formatExerciseSummary(summary({ ...pushUp, reps: 20 }), KG_KM)).toBe('20回 × 3セット')
    expect(
      formatExerciseSummary(summary({ ...pushUp, uniform: false, totalReps: 55 }), KG_KM),
    ).toBe('合計 55回（3セット）')
  })

  describe('時間・距離', () => {
    const run = (values: Partial<ExerciseSummary>) =>
      summary({
        recordType: 'time_distance',
        name: 'ランニング',
        uniform: null,
        setCount: 1,
        ...values,
      })

    test('1回なら距離・時間・ペース', () => {
      expect(
        formatExerciseSummary(run({ durationSec: 1800, distanceKm: 5, paceSecPerKm: 360 }), KG_KM),
      ).toBe('5.00km 30:00（6:00/km）')
      // マイルで見る人には、マイルあたりのペース
      expect(
        formatExerciseSummary(run({ durationSec: 1800, distanceKm: 5, paceSecPerKm: 360 }), LB_MI),
      ).toBe('3.11mi 30:00（9:39/mi）')
    })

    test('距離がなければ時間だけ', () => {
      expect(formatExerciseSummary(run({ durationSec: 1800 }), KG_KM)).toBe('30:00')
    })

    test('2回以上なら合計と回数', () => {
      expect(
        formatExerciseSummary(run({ setCount: 2, durationSec: 3000, distanceKm: 8 }), KG_KM),
      ).toBe('合計 8.00km 50:00（2回）')
      expect(formatExerciseSummary(run({ setCount: 2, durationSec: 3000 }), KG_KM)).toBe(
        '合計 50:00（2回）',
      )
    })
  })
})
