import { type FormEvent, type RefObject, useEffect, useId, useRef } from 'react'
import { COMMENT_MAX_LENGTH, canPostComment, commentLength } from '../lib/comment/comment'
import styles from './CommentForm.module.css'

interface Props {
  /** 入力欄の名前（「コメントを入力」「メモを書く」「返信を入力」） */
  label: string
  submitLabel: string
  value: string
  onChange: (value: string) => void
  onSubmit: () => void
  busy: boolean
  /** 外からカーソルを合わせるときに使う（💬 を押したとき） */
  inputRef?: RefObject<HTMLTextAreaElement | null>
  /** 出したときにカーソルを合わせる（「返信」を押して出した入力欄） */
  focusOnOpen?: boolean
}

/**
 * コメント・返信の入力欄（docs/screens.md S-12、F-30）。1〜140文字。「N / 140」を出し、超えたら赤字にしてボタンを押せなくする。
 * 送信中も押せない
 */
export function CommentForm({
  label,
  submitLabel,
  value,
  onChange,
  onSubmit,
  busy,
  inputRef,
  focusOnOpen = false,
}: Props) {
  const countId = useId()
  const ownRef = useRef<HTMLTextAreaElement>(null)
  const ref = inputRef ?? ownRef
  useEffect(() => {
    // 「返信」を押した人が、そのまま書けるようにする（押した操作への応答なので、開いたときに勝手に移るのとは違う）
    if (focusOnOpen) ref.current?.focus()
  }, [focusOnOpen, ref])
  const length = commentLength(value)
  const over = length > COMMENT_MAX_LENGTH
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (canPostComment(value) && !busy) onSubmit()
  }
  return (
    <form className={styles.form} onSubmit={submit}>
      <textarea
        ref={ref}
        aria-label={label}
        aria-describedby={countId}
        placeholder={label}
        className={styles.input}
        rows={2}
        value={value}
        // カーソルを合わせたら、@ユーザー名 のあとから書けるようにする
        onFocus={(event) => {
          const end = event.currentTarget.value.length
          event.currentTarget.setSelectionRange(end, end)
        }}
        onChange={(event) => onChange(event.target.value)}
      />
      <div className={styles.actions}>
        <span id={countId} className={styles.count} data-over={over}>
          {length} / {COMMENT_MAX_LENGTH}
        </span>
        <button type="submit" className="button-primary" disabled={!canPostComment(value) || busy}>
          {submitLabel}
        </button>
      </div>
    </form>
  )
}
