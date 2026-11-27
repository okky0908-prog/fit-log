import { lazy, Suspense, useState } from 'react'
import type { BodyPoint } from '../lib/stats/body'
import styles from './StatsChart.module.css'

// グラフの部品（Recharts）は大きいので、グラフの画面を開いたときに後から読み込む（docs/tech-stack.md 3.5）
const BodyLineChart = lazy(() => import('./BodyLineChart'))

interface Props {
  points: BodyPoint[]
  /** 体重の単位（kg・lb） */
  unit: string
}

/**
 * 体重・体脂肪率の推移（docs/screens.md S-25、「グラフ（共通）」）。画面の読み上げのため、同じ内容の表を「表で見る」で開ける。
 * データがなければ「この期間の記録はありません」
 */
export function BodyTrendChart({ points, unit }: Props) {
  const [tableOpen, setTableOpen] = useState(false)
  if (points.length === 0) {
    return <p className={styles.empty}>この期間の記録はありません</p>
  }
  return (
    <>
      <Suspense fallback={<p className={styles.empty}>グラフを読み込み中…</p>}>
        <BodyLineChart points={points} unit={unit} />
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
          <caption className="visually-hidden">体重と体脂肪率の推移</caption>
          <thead>
            <tr>
              <th scope="col">日付</th>
              <th scope="col">体重</th>
              <th scope="col">体脂肪率</th>
            </tr>
          </thead>
          <tbody>
            {points.map((point) => (
              <tr key={point.label}>
                <td>{point.label}</td>
                <td>{`${point.weight.toFixed(1)}${unit}`}</td>
                <td>{point.bodyFat === null ? '—' : `${point.bodyFat.toFixed(1)}%`}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  )
}
