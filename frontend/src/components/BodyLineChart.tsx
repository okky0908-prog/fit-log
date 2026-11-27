import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { BodyPoint } from '../lib/stats/body'
import { axisLabel } from '../lib/stats/stats'

interface Props {
  points: BodyPoint[]
  /** 体重の単位（kg・lb） */
  unit: string
}

/**
 * Recharts の体重・体脂肪率の折れ線グラフ（docs/screens.md S-25「右と左で別の目盛り」）。BodyTrendChart が後から読み込む。
 * 体重は実線で左の目盛り、体脂肪率は破線で右の目盛り（色だけでなく線の形でも見分けられるように）。体脂肪率のない日は線をつなぐ
 */
export default function BodyLineChart({ points, unit }: Props) {
  return (
    <ResponsiveContainer width="100%" height={240}>
      <LineChart
        data={points}
        title="体重と体脂肪率の推移"
        margin={{ top: 8, right: 0, bottom: 0, left: 0 }}
      >
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
          yAxisId="weight"
          width={56}
          domain={['auto', 'auto']}
          tick={{ fontSize: 12 }}
          tickLine={false}
          stroke="var(--color-text-muted)"
          tickFormatter={(value: number) => `${value}${unit}`}
        />
        <YAxis
          yAxisId="fat"
          orientation="right"
          width={48}
          domain={['auto', 'auto']}
          tick={{ fontSize: 12 }}
          tickLine={false}
          stroke="var(--color-text-muted)"
          tickFormatter={(value: number) => `${value}%`}
        />
        <Tooltip
          formatter={(value, name) =>
            name === '体重' ? [`${Number(value).toFixed(1)}${unit}`, name] : [`${value}%`, name]
          }
        />
        <Legend wrapperStyle={{ fontSize: 12 }} />
        <Line
          yAxisId="weight"
          type="monotone"
          dataKey="weight"
          name="体重"
          stroke="var(--color-primary)"
          strokeWidth={2}
          dot={{ r: 3, fill: 'var(--color-primary)' }}
          isAnimationActive={false}
        />
        <Line
          yAxisId="fat"
          type="monotone"
          dataKey="bodyFat"
          name="体脂肪率"
          stroke="var(--color-text-muted)"
          strokeWidth={2}
          strokeDasharray="5 4"
          dot={{ r: 3, fill: 'var(--color-text-muted)' }}
          connectNulls
          isAnimationActive={false}
        />
      </LineChart>
    </ResponsiveContainer>
  )
}
