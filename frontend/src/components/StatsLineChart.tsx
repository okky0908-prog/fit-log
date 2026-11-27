import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { axisLabel, type ChartPoint } from '../lib/stats/stats'

interface Props {
  title: string
  valueName: string
  points: ChartPoint[]
  format: (value: number) => string
}

/**
 * Recharts の折れ線グラフ（種目ごとの推移）。StatsChart が後から読み込む（React.lazy は default export を読むので、default で出す）。
 * 値を押す・マウスを乗せる・キーボードの矢印で、その値を出す（Recharts の accessibilityLayer）。
 * 縦軸は 0 からでなく値の範囲に合わせ（重量の伸びが見えるように）、横軸の日付は重ならないよう間引く
 */
export default function StatsLineChart({ title, valueName, points, format }: Props) {
  return (
    <ResponsiveContainer width="100%" height={220}>
      <LineChart data={points} title={title} margin={{ top: 8, right: 16, bottom: 0, left: 0 }}>
        <CartesianGrid vertical={false} stroke="var(--color-border)" />
        <XAxis
          dataKey="label"
          interval="preserveStartEnd"
          minTickGap={16}
          tickFormatter={axisLabel}
          tick={{ fontSize: 12 }}
          tickLine={false}
          stroke="var(--color-text-muted)"
        />
        <YAxis
          width={72}
          domain={['auto', 'auto']}
          tickLine={false}
          tick={{ fontSize: 12 }}
          stroke="var(--color-text-muted)"
          tickFormatter={(value: number) => format(value)}
        />
        <Tooltip formatter={(value) => [format(Number(value)), valueName]} />
        <Line
          type="monotone"
          dataKey="value"
          name={valueName}
          stroke="var(--color-primary)"
          strokeWidth={2}
          dot={{ r: 3, fill: 'var(--color-primary)' }}
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
