import type { BodyTrend } from '../api/types'
import { bodyWeightValue } from '../format/units'

/** S-25 のグラフの点（体重は単位に換算済み。体脂肪率は記録がなければ null） */
export interface BodyPoint {
  label: string
  weight: number
  bodyFat: number | null
}

/** 体重・体脂肪率のグラフの点（日付の古い順）。体重は記録の刻み（kg は 0.1、lb は 0.2）に丸める */
export function bodyTrendPoints(trend: BodyTrend, unit: 'kg' | 'lb'): BodyPoint[] {
  return trend.points.map((point) => ({
    label: point.date.replaceAll('-', '/'),
    weight: Number(bodyWeightValue(point.weightKg, unit)),
    bodyFat: point.bodyFatPct,
  }))
}
