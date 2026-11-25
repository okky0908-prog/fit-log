import { useLocation, useNavigate } from 'react-router'
import styles from './BackButton.module.css'

interface Props {
  /** 戻る先がないとき（URL を直接開いたなど）の移動先 */
  fallback: string
  /** 「←」の横に出す文字（画面の名前など） */
  children: string
}

/**
 * ← 直前の画面に戻る。アプリの中に戻る先がなければ fallback へ。
 * 読み上げの名前は「佐藤花子（戻る）」のように、見えている文字を含める（WCAG 2.5.3。音声で操作する人が、見えている文字で押せるように）
 */
export function BackButton({ fallback, children }: Props) {
  const navigate = useNavigate()
  const location = useLocation()
  // 最初に開いた画面の location.key は 'default'
  const hasPrevious = location.key !== 'default'
  return (
    <button
      type="button"
      className={styles.back}
      onClick={() => (hasPrevious ? void navigate(-1) : void navigate(fallback))}
    >
      <span aria-hidden="true">← </span>
      {children}
      <span className="visually-hidden">（戻る）</span>
    </button>
  )
}
