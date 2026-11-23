import { useEffect, useRef } from 'react'
import styles from './LoadMore.module.css'

interface Props {
  loading: boolean
  onLoad: () => void
}

/** 続きの読み込み。下までスクロールしたら読み込み、ボタンでも読み込める（キーボード・読み上げで使えるように） */
export function LoadMore({ loading, onLoad }: Props) {
  const ref = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const target = ref.current
    if (!target || typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting) && !loading) onLoad()
    })
    observer.observe(target)
    return () => observer.disconnect()
  }, [loading, onLoad])
  return (
    <button
      ref={ref}
      type="button"
      className={`button-secondary ${styles.more}`}
      disabled={loading}
      onClick={onLoad}
    >
      {loading ? '読み込み中…' : 'さらに表示'}
    </button>
  )
}
