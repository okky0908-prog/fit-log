import { describe, expect, test } from 'vitest'
import type { MonthlyStats, StatsSummary, WeeklyStats } from '../api/types'
import type { Units } from '../format/workout'
import {
  axisLabel,
  bodyPartRows,
  dailyPoints,
  formatDiff,
  formatMetric,
  hasChartData,
  summaryItems,
  weeklyPoints,
} from './stats'

// S-20 の集計値・前の期間との差・グラフの点・部位別のセット数の表示（docs/screens.md S-20）

const KG: Units = { weightUnit: 'kg', distanceUnit: 'km' }
const LB: Units = { weightUnit: 'lb', distanceUnit: 'mi' }

function summary(values: Partial<StatsSummary> = {}): StatsSummary {
  return {
    trainingDays: 0,
    totalVolumeKg: 0,
    totalDurationSec: 0,
    totalDistanceKm: 0,
    caloriesKcal: null,
    caloriesExcludedWorkoutCount: 0,
    ...values,
  }
}

describe('前の期間との差', () => {
  test('増えたら＋、減ったら−、同じなら±0（色だけでなく記号でも分かるように）', () => {
    expect(formatDiff(4, 3)).toEqual({ text: '+1', sign: 'up' })
    expect(formatDiff(95, 105)).toEqual({ text: '−10', sign: 'down' })
    expect(formatDiff(3, 3)).toEqual({ text: '±0', sign: 'same' })
  })

  test('大きな数は3桁ごとに区切り、小数の桁を指定できる', () => {
    expect(formatDiff(12450, 11250)).toEqual({ text: '+1,200', sign: 'up' })
    expect(formatDiff(15, 12.5, 2)).toEqual({ text: '+2.50', sign: 'up' })
  })

  test('表示の桁に丸めた差が0なら±0', () => {
    expect(formatDiff(15.001, 15, 2)).toEqual({ text: '±0', sign: 'same' })
  })
})

describe('集計値', () => {
  test('トレーニング日数・総挙上量・運動時間（分）・距離と、それぞれの差', () => {
    const items = summaryItems(
      summary({
        trainingDays: 4,
        totalVolumeKg: 12450,
        totalDurationSec: 5700,
        totalDistanceKm: 15,
      }),
      summary({
        trainingDays: 3,
        totalVolumeKg: 11650,
        totalDurationSec: 6300,
        totalDistanceKm: 12,
      }),
      KG,
    )

    expect(items).toEqual([
      { label: 'トレーニング日数', value: '4日', diff: { text: '+1', sign: 'up' } },
      { label: '総挙上量', value: '12,450kg', diff: { text: '+800', sign: 'up' } },
      { label: '運動時間', value: '95分', diff: { text: '−10', sign: 'down' } },
      { label: '距離', value: '15.00km', diff: { text: '+3.00', sign: 'up' } },
    ])
  })

  test('lb・マイルに換算する（総挙上量は整数に丸める）', () => {
    const items = summaryItems(
      summary({ totalVolumeKg: 1000, totalDistanceKm: 16.09344 }),
      summary(),
      LB,
    )

    // 1000kg ÷ 0.45359237 = 2204.6… → 2,205lb、16.09344km = 10.00mi
    expect(items[1]).toEqual({
      label: '総挙上量',
      value: '2,205lb',
      diff: { text: '+2,205', sign: 'up' },
    })
    expect(items[3].value).toBe('10.00mi')
  })

  test('運動時間は分に四捨五入する', () => {
    expect(summaryItems(summary({ totalDurationSec: 89 }), summary(), KG)[2].value).toBe('1分')
    expect(summaryItems(summary({ totalDurationSec: 29 }), summary(), KG)[2].value).toBe('0分')
  })
})

describe('グラフの点', () => {
  const weekly: WeeklyStats = {
    from: '2026-11-09',
    to: '2026-11-15',
    summary: summary(),
    previous: summary(),
    daily: [
      { date: '2026-11-09', volumeKg: 3200, durationSec: 0 },
      { date: '2026-11-10', volumeKg: 0, durationSec: 1830 },
      { date: '2026-11-11', volumeKg: 0, durationSec: 0 },
      { date: '2026-11-12', volumeKg: 0, durationSec: 0 },
      { date: '2026-11-13', volumeKg: 0, durationSec: 0 },
      { date: '2026-11-14', volumeKg: 0, durationSec: 0 },
      { date: '2026-11-15', volumeKg: 100, durationSec: 0 },
    ],
    bodyParts: [],
  }

  test('週間：曜日ごとに、総挙上量か運動時間（分）', () => {
    expect(dailyPoints(weekly, 'volume', KG).map((p) => p.label)).toEqual([
      '月',
      '火',
      '水',
      '木',
      '金',
      '土',
      '日',
    ])
    expect(dailyPoints(weekly, 'volume', KG)[0].value).toBe(3200)
    expect(dailyPoints(weekly, 'duration', KG)[1].value).toBe(31)
  })

  test('週間：総挙上量は単位に換算する', () => {
    expect(dailyPoints(weekly, 'volume', LB)[6].value).toBe(220)
  })

  const monthly: MonthlyStats = {
    from: '2026-11-01',
    to: '2026-11-30',
    summary: summary(),
    previous: summary(),
    weekly: [
      { weekStart: '2026-10-26', volumeKg: 600, durationSec: 0, distanceKm: 0, trainingDays: 1 },
      { weekStart: '2026-11-02', volumeKg: 0, durationSec: 600, distanceKm: 0, trainingDays: 1 },
    ],
    bodyParts: [],
  }

  test('月間：週ごと。月をまたぐ週は、その月の最初の日から（「11/1〜」）', () => {
    expect(weeklyPoints(monthly, 'volume', KG)).toEqual([
      { label: '11/1〜', value: 600 },
      { label: '11/2〜', value: 0 },
    ])
    expect(weeklyPoints(monthly, 'duration', KG)[1].value).toBe(10)
  })

  test('値の表示（グラフの値を押したとき・表で見る）', () => {
    expect(formatMetric(12450, 'volume', KG)).toBe('12,450kg')
    expect(formatMetric(2205, 'volume', LB)).toBe('2,205lb')
    expect(formatMetric(95, 'duration', KG)).toBe('95分')
  })

  test('すべて0ならデータなし', () => {
    expect(hasChartData([{ label: '月', value: 0 }])).toBe(false)
    expect(
      hasChartData([
        { label: '月', value: 0 },
        { label: '火', value: 1 },
      ]),
    ).toBe(true)
    expect(hasChartData([])).toBe(false)
  })
})

describe('部位別のセット数', () => {
  test('部位の名前と割合（四捨五入）を、届いた順（多い順）に', () => {
    expect(
      bodyPartRows([
        { bodyPart: 'chest', setCount: 12 },
        { bodyPart: 'legs', setCount: 9 },
        { bodyPart: 'cardio', setCount: 19 },
      ]),
    ).toEqual([
      { label: '胸', setCount: 12, percent: 30 },
      { label: '脚', setCount: 9, percent: 23 },
      { label: '有酸素', setCount: 19, percent: 48 },
    ])
  })

  test('なければ空', () => {
    expect(bodyPartRows([])).toEqual([])
  })
})

describe('グラフの横軸の目盛り', () => {
  test('スマートフォンの幅でも全部出るよう、「〜」と、月の「月」を省く（表とツールチップには省かずに出す）', () => {
    expect(axisLabel('11/2〜')).toBe('11/2')
    expect(axisLabel('1月')).toBe('1')
    expect(axisLabel('12月')).toBe('12')
  })

  test('曜日の「月」（月曜）は省かない', () => {
    expect(axisLabel('月')).toBe('月')
    expect(axisLabel('火')).toBe('火')
  })

  test('日付（推移のグラフ）は年と、月・日の前の 0 を省く', () => {
    expect(axisLabel('2026/11/14')).toBe('11/14')
    expect(axisLabel('2025/01/05')).toBe('1/5')
  })
})
