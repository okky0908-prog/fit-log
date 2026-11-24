import { useState } from 'react'
import { ImageViewer } from './ImageViewer'
import styles from './WorkoutImages.module.css'

/** 記録の画像の並び（1〜4枚。docs/screens.md「投稿カード」）。押すと拡大表示 */
export function WorkoutImages({ urls }: { urls: string[] }) {
  const [viewing, setViewing] = useState<number | null>(null)
  return (
    <>
      <div className={`${styles.images} ${styles[`images${urls.length}`]}`}>
        {urls.map((url, index) => (
          <button
            key={url}
            type="button"
            className={styles.imageButton}
            aria-label={`画像 ${index + 1} / ${urls.length} を拡大`}
            onClick={() => setViewing(index)}
          >
            <img src={url} alt={`画像 ${index + 1} / ${urls.length}`} className={styles.image} />
          </button>
        ))}
      </div>
      {viewing !== null && (
        <ImageViewer
          urls={urls}
          index={viewing}
          onMove={setViewing}
          onClose={() => setViewing(null)}
        />
      )}
    </>
  )
}
