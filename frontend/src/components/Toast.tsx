import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react'
import { ToastContext } from '../lib/toast'
import styles from './Toast.module.css'

/** お知らせを出しておく時間（docs/screens.md「確認ダイアログ・お知らせ」） */
const DURATION_MS = 3000

/**
 * 画面の下に短いお知らせを出す。続けて出したら新しいものに替える。
 * 読み上げに伝わるよう、お知らせの場所（output。役割は status）は空のときも置いておく
 */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const show = useCallback((next: string) => {
    if (timer.current) clearTimeout(timer.current)
    setMessage(next)
    timer.current = setTimeout(() => setMessage(null), DURATION_MS)
  }, [])

  useEffect(
    () => () => {
      if (timer.current) clearTimeout(timer.current)
    },
    [],
  )

  return (
    <ToastContext.Provider value={show}>
      {children}
      <output className={styles.region}>
        {message && <p className={styles.toast}>{message}</p>}
      </output>
    </ToastContext.Provider>
  )
}
