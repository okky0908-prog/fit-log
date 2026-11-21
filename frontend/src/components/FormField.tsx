import { useId, type InputHTMLAttributes } from 'react'
import styles from './FormField.module.css'

interface Props extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** 入力のエラー。あれば入力欄の下に出し、読み上げでも入力欄と結び付ける */
  error?: string
}

/** ラベル・入力欄・エラーの文言をまとめた入力の項目 */
export function FormField({ label, error, ...inputProps }: Props) {
  const id = useId()
  const errorId = `${id}-error`
  return (
    <div className={styles.field}>
      <label htmlFor={id} className={styles.label}>
        {label}
      </label>
      <input
        id={id}
        className={styles.input}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : undefined}
        {...inputProps}
      />
      {error && (
        <p id={errorId} className={styles.error}>
          {error}
        </p>
      )}
    </div>
  )
}
