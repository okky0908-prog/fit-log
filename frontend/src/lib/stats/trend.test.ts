import { describe, expect, test } from 'vitest'
import type { Exercise, ExerciseTrend, TrendPoint, UsedExercise } from '../api/types'
import type { Units } from '../format/workout'
import {
  bestLabel,
  formatTrendValue,
  groupByBodyPart,
  rangeFromParam,
  trendPoints,
  trendRow,
  trendViews,
  viewFromParam,
} from './trend'

// S-22 種目ごとの推移の表示（docs/screens.md S-22、F-84・F-85）

const KG: Units = { weightUnit: 'kg', distanceUnit: 'km' }
const LB: Units = { weightUnit: 'lb', distanceUnit: 'mi' }

const BENCH: Exercise = {
  id: 1,
  name: 'ベンチプレス',
  bodyPart: 'chest',
  recordType: 'weight_reps',
  isRunning: false,
  custom: false,
}
const PUSH_UP: Exercise = { ...BENCH, id: 8, name: '腕立て伏せ', recordType: 'reps' }
const BIKE: Exercise = {
  ...BENCH,
  id: 80,
  name: 'サイクリング',
  bodyPart: 'cardio',
  recordType: 'time_distance',
}
const RUN: Exercise = { ...BIKE, id: 48, name: 'ランニング', isRunning: true }

function point(values: Partial<TrendPoint>): TrendPoint {
  return {
    date: '2026-11-14',
    workoutId: 1,
    maxWeightKg: null,
    maxE1rmKg: null,
    maxWeightReps: null,
    totalReps: null,
    maxReps: null,
    totalDurationSec: null,
    totalDistanceKm: null,
    paceSecPerKm: null,
    ...values,
  }
}

function trend(exercise: Exercise, points: TrendPoint[]): ExerciseTrend {
  return { exercise, personalBest: null, bestE1rm: null, points }
}

describe('期間', () => {
  test('3m・1y・all だけを受け付け、それ以外・なければ 3か月', () => {
    expect(rangeFromParam(null)).toBe('3m')
    expect(rangeFromParam('1y')).toBe('1y')
    expect(rangeFromParam('all')).toBe('all')
    expect(rangeFromParam('6m')).toBe('3m')
  })
})

describe('グラフの値の切り替え', () => {
  test('記録方式ごとに選べる値。ランニングだけペースも選べる', () => {
    expect(trendViews(BENCH).map((v) => v.label)).toEqual(['最大重量', '推定 1RM'])
    expect(trendViews(PUSH_UP).map((v) => v.label)).toEqual(['合計回数', '最大回数'])
    expect(trendViews(BIKE).map((v) => v.label)).toEqual(['合計距離', '合計時間'])
    expect(trendViews(RUN).map((v) => v.label)).toEqual(['合計距離', '合計時間', 'ペース'])
  })

  test('URL の値がその種目で選べなければ、最初の値', () => {
    expect(viewFromParam('e1rm', BENCH)).toBe('e1rm')
    expect(viewFromParam(null, BENCH)).toBe('max')
    expect(viewFromParam('pace', BIKE)).toBe('distance')
    expect(viewFromParam('pace', RUN)).toBe('pace')
    expect(viewFromParam('total', BENCH)).toBe('max')
  })
})

describe('グラフの点', () => {
  test('重量は単位に換算し、値のない日は点を打たない', () => {
    const points = [
      point({ date: '2026-09-01', maxWeightKg: 61.235, maxE1rmKg: 70 }),
      point({ date: '2026-09-08' }),
    ]
    expect(trendPoints(trend(BENCH, points), 'max', KG)).toEqual([
      { label: '2026/09/01', value: 61.25 },
    ])
    expect(trendPoints(trend(BENCH, points), 'max', LB)).toEqual([
      { label: '2026/09/01', value: 135 },
    ])
    expect(trendPoints(trend(BENCH, points), 'e1rm', KG)).toEqual([
      { label: '2026/09/01', value: 70 },
    ])
  })

  test('回数のみは合計回数・最大回数', () => {
    const points = [point({ totalReps: 60, maxReps: 25 })]
    expect(trendPoints(trend(PUSH_UP, points), 'total', KG)[0].value).toBe(60)
    expect(trendPoints(trend(PUSH_UP, points), 'max', KG)[0].value).toBe(25)
  })

  test('時間・距離は合計距離（単位に換算）・合計時間（分）・ペース（単位あたりの秒）', () => {
    const points = [
      point({ totalDurationSec: 1830, totalDistanceKm: 8.04672, paceSecPerKm: 360 }),
      point({ date: '2026-11-15', totalDurationSec: 600 }),
    ]
    expect(trendPoints(trend(RUN, points), 'distance', KG)).toEqual([
      { label: '2026/11/14', value: 8.05 },
    ])
    expect(trendPoints(trend(RUN, points), 'distance', LB)[0].value).toBe(5)
    expect(trendPoints(trend(RUN, points), 'time', KG).map((p) => p.value)).toEqual([31, 10])
    expect(trendPoints(trend(RUN, points), 'pace', LB)).toEqual([
      { label: '2026/11/14', value: 579 },
    ])
  })

  test('グラフ・表の値の表示', () => {
    expect(formatTrendValue(62.5, 'max', BENCH, KG)).toBe('62.5kg')
    expect(formatTrendValue(135, 'e1rm', BENCH, LB)).toBe('135lb')
    expect(formatTrendValue(25, 'max', PUSH_UP, KG)).toBe('25回')
    expect(formatTrendValue(5, 'distance', RUN, LB)).toBe('5.00mi')
    expect(formatTrendValue(31, 'time', RUN, KG)).toBe('31分')
    expect(formatTrendValue(579, 'pace', RUN, LB)).toBe('9:39/mi')
  })
})

describe('記録の一覧の行', () => {
  test('重量 × 回数：最大重量 × 回数と推定 1RM。推定 1RM がなければ出さない', () => {
    expect(
      trendRow(point({ maxWeightKg: 95, maxWeightReps: 3, maxE1rmKg: 104.5 }), BENCH, KG),
    ).toBe('最大 95kg × 3回・推定 1RM 104.5kg')
    expect(trendRow(point({ maxWeightKg: 60, maxWeightReps: 12 }), BENCH, KG)).toBe(
      '最大 60kg × 12回',
    )
  })

  test('重量 × 回数で挙がったセットがない日は、そう書く', () => {
    expect(trendRow(point({}), BENCH, KG)).toBe('挙がったセットなし')
  })

  test('回数のみ：合計回数と最大回数', () => {
    expect(trendRow(point({ totalReps: 60, maxReps: 25 }), PUSH_UP, KG)).toBe(
      '合計 60回・最大 25回',
    )
  })

  test('時間・距離：合計時間・合計距離・ペース（ないものは出さない）', () => {
    expect(
      trendRow(point({ totalDurationSec: 1800, totalDistanceKm: 5, paceSecPerKm: 360 }), RUN, KG),
    ).toBe('30:00・5.00km・6:00/km')
    expect(trendRow(point({ totalDurationSec: 600 }), RUN, KG)).toBe('10:00')
    expect(trendRow(point({ totalDurationSec: 3600, totalDistanceKm: 20 }), BIKE, LB)).toBe(
      '1:00:00・12.43mi',
    )
  })
})

describe('自己ベスト・推定 1RM の最大', () => {
  test('「100kg（2026/10/20）」の形（重量の単位に換算）', () => {
    expect(bestLabel(100, '2026-10-20', KG)).toBe('100kg（2026/10/20）')
    expect(bestLabel(61.235, '2026-10-20', LB)).toBe('135lb（2026/10/20）')
  })
})

describe('記録した種目の一覧', () => {
  test('部位ごとにまとめる（届いた順のまま）', () => {
    const used: UsedExercise[] = [
      { exercise: BENCH, lastPerformedOn: '2026-11-10' },
      { exercise: PUSH_UP, lastPerformedOn: '2026-11-01' },
      { exercise: RUN, lastPerformedOn: '2026-10-01' },
    ]
    expect(groupByBodyPart(used)).toEqual([
      { bodyPart: 'chest', label: '胸', items: [used[0], used[1]] },
      { bodyPart: 'cardio', label: '有酸素', items: [used[2]] },
    ])
    expect(groupByBodyPart([])).toEqual([])
  })
})
