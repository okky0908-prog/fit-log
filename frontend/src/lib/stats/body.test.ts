import { describe, expect, test } from 'vitest'
import { bodyTrendPoints } from './body'

// S-25 の体重・体脂肪率のグラフの点（docs/screens.md S-25、F-92）

describe('体重・体脂肪率のグラフの点', () => {
  test('日付は「2026/11/14」、体重は単位に換算して記録の刻みに丸め、体脂肪率がなければ null', () => {
    const trend = {
      points: [
        { date: '2026-11-01', weightKg: 68.039, bodyFatPct: 18.5 },
        { date: '2026-11-14', weightKg: 68.13, bodyFatPct: null },
      ],
    }
    expect(bodyTrendPoints(trend, 'kg')).toEqual([
      { label: '2026/11/01', weight: 68, bodyFat: 18.5 },
      { label: '2026/11/14', weight: 68.1, bodyFat: null },
    ])
    expect(bodyTrendPoints(trend, 'lb').map((p) => p.weight)).toEqual([150, 150.2])
  })
})
