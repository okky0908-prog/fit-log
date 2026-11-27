import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { ChartPoint } from '../lib/stats/stats'

interface Props {
  title: string
  valueName: string
  points: ChartPoint[]
  format: (value: number) => string
}

/**
 * Recharts の棒グラフ。StatsChart が後から読み込む（React.lazy は default export を読むので、default で出す）。
 * 値を押す・マウスを乗せる・キーボードの矢印で、その値を出す（Recharts の accessibilityLayer）
 */
export default function StatsBarChart({ title, valueName, points, format }: Props) {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <BarChart data={points} title={title} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
        <CartesianGrid vertical={false} stroke="var(--color-border)" />
        {/* 月間の週（最大6本）もスマートフォンの幅で全部出るよう、目盛りを間引かず、文字を小さくし、「〜」を省く（表とツールチップには出す） */}
        <XAxis
          dataKey="label"
          interval={0}
          tickFormatter={(label: string) => label.replace('〜', '')}
          tick={{ fontSize: 12 }}
          tickLine={false}
          stroke="var(--color-text-muted)"
        />
        <YAxis
          width={64}
          tickLine={false}
          stroke="var(--color-text-muted)"
          tickFormatter={(value: number) => value.toLocaleString('ja-JP')}
        />
        <Tooltip
          cursor={{ fill: 'var(--color-primary-surface)' }}
          formatter={(value) => [format(Number(value)), valueName]}
        />
        <Bar dataKey="value" name={valueName} fill="var(--color-primary)" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  )
}
