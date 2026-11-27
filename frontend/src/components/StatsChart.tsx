import { lazy, Suspense, useState } from 'react'
import { type ChartPoint, hasChartData } from '../lib/stats/stats'
import styles from './StatsChart.module.css'

// グラフの部品（Recharts）は大きいので、グラフの画面を開いたときに後から読み込む（docs/tech-stack.md 3.5）
const StatsBarChart = lazy(() => import('./StatsBarChart'))

interface Props {
  /** グラフの説明（「曜日ごとの総挙上量」）。表の見出しにも使う */
  title: string
  /** 横軸の見出し（「曜日」「週」） */
  labelHeader: string
  /** 値の名前（「総挙上量」「運動時間」） */
  valueName: string
  points: ChartPoint[]
  format: (value: number) => string
}

/**
 * 棒グラフ（docs/screens.md「グラフ（共通）」）。値を押す・マウスを乗せると、その値を出す。
 * 画面の読み上げのため、同じ内容の表を「表で見る」で開ける。データがなければ「この期間の記録はありません」
 */
export function StatsChart({ title, labelHeader, valueName, points, format }: Props) {
  const [tableOpen, setTableOpen] = useState(false)
  if (!hasChartData(points)) {
    return <p className={styles.empty}>この期間の記録はありません</p>
  }
  return (
    <>
      <Suspense fallback={<p className={styles.empty}>グラフを読み込み中…</p>}>
        <StatsBarChart title={title} valueName={valueName} points={points} format={format} />
      </Suspense>
      <button
        type="button"
        className={styles.toggle}
        aria-expanded={tableOpen}
        onClick={() => setTableOpen(!tableOpen)}
      >
        {tableOpen ? '表を閉じる' : '表で見る'}
      </button>
      {tableOpen && (
        <table className={styles.table}>
          <caption className="visually-hidden">{title}</caption>
          <thead>
            <tr>
              <th scope="col">{labelHeader}</th>
              <th scope="col">{valueName}</th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={point.label}>
                <td>{point.label}</td>
                <td>{format(point.value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
