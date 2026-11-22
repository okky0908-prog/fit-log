import styles from './WorkoutImagesField.module.css'

/** 画像の欄に並べる1枚。今の画像（編集）か、新しく選んで縮小した画像 */
export type ImageItem =
  | { kind: 'existing'; key: string; id: number; url: string }
  | { kind: 'new'; key: string; blob: Blob; url: string }

export const MAX_IMAGES = 4

interface Props {
  images: ImageItem[]
  /** 縮小している間（「＋」を押せなくする） */
  busy: boolean
  /** 選べなかった理由・サーバーが受け付けなかった理由 */
  message: string | null
  onAdd: (files: File[]) => void
  onRemove: (index: number) => void
}

/** 記録の画像の欄（docs/screens.md S-13「画像」）。4枚まで。プレビューを並べ、× で外す */
export function WorkoutImagesField({ images, busy, message, onAdd, onRemove }: Props) {
  return (
    <div className={styles.field}>
      <span className={styles.label}>画像</span>
      <ul className={styles.list}>
        {images.map((image, index) => (
          <li key={image.key} className={styles.item}>
            <img src={image.url} alt={`画像 ${index + 1}`} className={styles.thumbnail} />
            <button
              type="button"
              className={styles.remove}
              aria-label={`画像 ${index + 1} を外す`}
              onClick={() => onRemove(index)}
            >
              ×
            </button>
          </li>
        ))}
        {images.length < MAX_IMAGES && (
          <li>
            <label className={busy ? styles.addBusy : styles.add}>
              ＋
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                aria-label="画像を追加"
                className="visually-hidden"
                disabled={busy}
                onChange={(event) => {
                  const files = [...(event.target.files ?? [])]
                  // 同じ画像をもう一度選べるよう、選んだ内容を空に戻す
                  event.target.value = ''
                  onAdd(files)
                }}
              />
            </label>
          </li>
        )}
      </ul>
      {busy && <p className={styles.note}>画像を縮小しています…</p>}
      {message && <p className={styles.error}>{message}</p>}
    </div>
  )
}
