import { useLocation, useNavigate } from 'react-router'
import styles from './BackButton.module.css'

interface Props {
  /** 戻る先がないとき（URL を直接開いたなど）の移動先 */
  fallback: string
  /** 「←」の横に出す文字（画面の名前など） */
  children: string
}

/** ← 直前の画面に戻る。アプリの中に戻る先がなければ fallback へ */
export function BackButton({ fallback, children }: Props) {
  const navigate = useNavigate()
  const location = useLocation()
  // 最初に開いた画面の location.key は 'default'
  const hasPrevious = location.key !== 'default'
  return (
    <button
      type="button"
      className={styles.back}
      aria-label="戻る"
      onClick={() => (hasPrevious ? void navigate(-1) : void navigate(fallback))}
    >
      ← {children}
    </button>
  )
}
