import { useEffect, useRef } from 'react'
import styles from './ImageViewer.module.css'

interface Props {
  urls: string[]
  index: number
  onMove: (index: number) => void
  onClose: () => void
}

/**
 * 画像の拡大表示（docs/screens.md「投稿カード」の画像）。Esc・外側のクリックで閉じる。
 * 複数枚なら ‹ › と左右の矢印キーで移る（端では止まる）
 */
export function ImageViewer({ urls, index, onMove, onClose }: Props) {
  const title = `画像 ${index + 1} / ${urls.length}`
  const closeRef = useRef<HTMLButtonElement>(null)
  const hasPrevious = index > 0
  const hasNext = index < urls.length - 1

  useEffect(() => {
    closeRef.current?.focus()
  }, [])

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
      if (event.key === 'ArrowLeft' && hasPrevious) onMove(index - 1)
      if (event.key === 'ArrowRight' && hasNext) onMove(index + 1)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [index, hasPrevious, hasNext, onMove, onClose])

  return (
    <div className={styles.backdrop}>
      {/* 外側を押したら閉じる。キーボードでは Esc で閉じる */}
      <button
        ref={closeRef}
        type="button"
        className={styles.outside}
        aria-label="拡大表示を閉じる"
        onClick={onClose}
      />
      <dialog open className={styles.viewer} aria-modal="true" aria-label={title}>
        <img src={urls[index]} alt={title} className={styles.image} />
        {urls.length > 1 && (
          <>
            <button
              type="button"
              className={`${styles.nav} ${styles.previous}`}
              aria-label="前の画像"
              disabled={!hasPrevious}
              onClick={() => onMove(index - 1)}
            >
              ‹
            </button>
            <button
              type="button"
              className={`${styles.nav} ${styles.next}`}
              aria-label="次の画像"
              disabled={!hasNext}
              onClick={() => onMove(index + 1)}
            >
              ›
            </button>
          </>
        )}
      </dialog>
    </div>
  )
}
