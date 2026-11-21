import { useEffect, useId, useRef } from 'react'
import styles from './ConfirmDialog.module.css'

interface Props {
  /** 問いかけ（「この記録を削除しますか？」）。ダイアログの名前にもなる */
  title: string
  /** 書き添える説明 */
  description?: string
  /** 実行するボタンの文言（「削除する」） */
  confirmLabel: string
  /** 実行中（ボタンを押せなくする） */
  busy?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/**
 * 取り消せない操作の確認（docs/screens.md「確認ダイアログ・お知らせ」）。Esc・「キャンセル」・外側のクリックで閉じる。
 * 開いたら「キャンセル」に移る（うっかり Enter で実行しないように）
 */
export function ConfirmDialog({
  title,
  description,
  confirmLabel,
  busy = false,
  onConfirm,
  onCancel,
}: Props) {
  const titleId = useId()
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    cancelRef.current?.focus()
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onCancel])

  return (
    <div className={styles.backdrop}>
      {/* 外側を押したら閉じる。キーボードでは Esc か「キャンセル」で閉じるので、Tab では止まらない */}
      <button
        type="button"
        className={styles.outside}
        aria-label="閉じる"
        tabIndex={-1}
        onClick={onCancel}
      />
      <dialog open className={styles.dialog} aria-modal="true" aria-labelledby={titleId}>
        <h2 id={titleId} className={styles.title}>
          {title}
        </h2>
        {description && <p className={styles.description}>{description}</p>}
        <div className={styles.actions}>
          <button ref={cancelRef} type="button" className="button-secondary" onClick={onCancel}>
            キャンセル
          </button>
          <button type="button" className={styles.danger} disabled={busy} onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </dialog>
    </div>
  )
}
