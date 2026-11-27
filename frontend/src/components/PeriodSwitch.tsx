import { Link } from 'react-router'
import styles from './PeriodSwitch.module.css'

interface Props {
  /** 表示中の期間（「2026/11/09（月）〜 11/15（日）」） */
  label: string
  /** 期間の単位の名前（「週」「月」）。ボタンの名前に使う（「前の週」） */
  unit: string
  /** 前の期間の URL */
  previous: string
  /** 次の期間の URL。未来の期間なら null（移れない） */
  next: string | null
  /** 今の期間（今週・今月）の URL。今の期間を表示中なら null */
  current: string | null
  /** 今の期間へ戻るボタンの文字（「今週」「今月」） */
  currentLabel: string
}

/**
 * 期間の切り替え（docs/screens.md「期間の切り替え（共通）」。S-20・S-21・S-22・S-25）。
 * 期間は URL に入れるので、リンクで移る（リロード・ブラウザの「戻る」で同じ期間を表示する）
 */
export function PeriodSwitch({ label, unit, previous, next, current, currentLabel }: Props) {
  return (
    <div className={styles.switch}>
      <Link to={previous} replace className={styles.arrow} aria-label={`前の${unit}`}>
        ‹
      </Link>
      <span className={styles.label}>{label}</span>
      {next === null ? (
        // 未来には移れない。位置がずれないよう、見えない枠だけ置く
        <span className={`${styles.arrow} ${styles.disabled}`} aria-hidden="true">
          ›
        </span>
      ) : (
        <Link to={next} replace className={styles.arrow} aria-label={`次の${unit}`}>
          ›
        </Link>
      )}
      {current !== null && (
        <Link to={current} replace className={styles.current}>
          {currentLabel}
        </Link>
      )}
    </div>
  )
}
