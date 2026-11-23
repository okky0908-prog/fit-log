import { type FormEvent, useEffect, useRef, useState } from 'react'
import { ApiError, createExercise, errorMessage, updateExercise } from '../lib/api/client'
import type { Exercise, RecordType } from '../lib/api/types'
import { bodyPartLabel } from '../lib/format/workout'
import { type CustomExerciseErrors, validateCustomExercise } from '../lib/validation/exercise'
import styles from './CustomExerciseForm.module.css'

const BODY_PARTS = ['chest', 'back', 'legs', 'shoulders', 'arms', 'abs', 'full_body', 'cardio']

const RECORD_TYPE_LABELS: Record<RecordType, string> = {
  weight_reps: '重量 × 回数',
  reps: '回数のみ',
  time_distance: '時間・距離',
}

const FIELDS = new Set(['name', 'bodyPart', 'recordType'])

type Props = {
  onSaved: (exercise: Exercise) => void
  onCancel: () => void
} & (
  | {
      mode: 'add'
      /** 名前の初期値（検索欄の文字） */
      initialName: string
      /** 「種目を変える」とき：この記録方式で追加する（選ばせない） */
      fixedRecordType?: RecordType
    }
  | { mode: 'edit'; exercise: Exercise }
)

/**
 * 自分専用の種目の追加（A-32）・名前と部位の変更（A-33）の入力（docs/screens.md S-13「種目の選択」、F-15・F-16）。
 * 記録方式はあとから変えられない。Esc・「キャンセル」で閉じる
 */
export function CustomExerciseForm(props: Props) {
  const { onSaved, onCancel } = props
  const editing = props.mode === 'edit' ? props.exercise : null
  const fixedRecordType = props.mode === 'add' ? props.fixedRecordType : undefined
  const [name, setName] = useState(
    editing ? editing.name : props.mode === 'add' ? props.initialName : '',
  )
  const [bodyPart, setBodyPart] = useState(editing?.bodyPart ?? '')
  const [recordType, setRecordType] = useState<string>(fixedRecordType ?? '')
  const [errors, setErrors] = useState<CustomExerciseErrors>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const nameRef = useRef<HTMLInputElement>(null)
  const title = editing ? '種目の名前・部位を変える' : '自分専用の種目を追加'

  useEffect(() => {
    nameRef.current?.focus()
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onCancel])

  async function submit(event: FormEvent) {
    event.preventDefault()
    const found = validateCustomExercise({
      name,
      bodyPart,
      recordType: editing ? null : recordType,
    })
    setErrors(found)
    setFailure(null)
    if (Object.keys(found).length > 0) return
    setSaving(true)
    try {
      const saved = editing
        ? await updateExercise(editing.id, { name, bodyPart })
        : await createExercise({ name, bodyPart, recordType })
      onSaved(saved)
    } catch (err) {
      // 項目に結び付くエラー（同じ名前など）はその項目の下に、それ以外（100件の上限など）は上に出す
      const fieldErrors = err instanceof ApiError ? err.fieldErrors : []
      const known = fieldErrors.filter((e) => FIELDS.has(e.field))
      if (known.length > 0) {
        setErrors(Object.fromEntries(known.map((e) => [e.field, e.message])))
      } else {
        setFailure(errorMessage(err))
      }
      setSaving(false)
    }
  }

  return (
    <div className={styles.backdrop}>
      <dialog open className={styles.dialog} aria-label={title}>
        <h3 className={styles.title}>{title}</h3>
        <form className={styles.form} onSubmit={(e) => void submit(e)} noValidate>
          {failure && <p className={styles.error}>{failure}</p>}
          <label className={styles.field}>
            種目の名前
            <input
              ref={nameRef}
              value={name}
              maxLength={60}
              aria-invalid={errors.name ? true : undefined}
              onChange={(e) => setName(e.target.value)}
            />
          </label>
          {errors.name && <p className={styles.error}>{errors.name}</p>}
          <label className={styles.field}>
            部位
            <select
              value={bodyPart}
              aria-invalid={errors.bodyPart ? true : undefined}
              onChange={(e) => setBodyPart(e.target.value)}
            >
              <option value="">選んでください</option>
              {BODY_PARTS.map((part) => (
                <option key={part} value={part}>
                  {bodyPartLabel(part)}
                </option>
              ))}
            </select>
          </label>
          {errors.bodyPart && <p className={styles.error}>{errors.bodyPart}</p>}
          {editing ? (
            <p className={styles.note}>記録方式は変えられません</p>
          ) : fixedRecordType ? (
            <p className={styles.note}>記録方式：{RECORD_TYPE_LABELS[fixedRecordType]}</p>
          ) : (
            <fieldset className={styles.recordTypes}>
              <legend>記録方式（あとから変えられません）</legend>
              {(Object.keys(RECORD_TYPE_LABELS) as RecordType[]).map((type) => (
                <label key={type} className={styles.radio}>
                  <input
                    type="radio"
                    name="recordType"
                    value={type}
                    checked={recordType === type}
                    onChange={() => setRecordType(type)}
                  />
                  {RECORD_TYPE_LABELS[type]}
                </label>
              ))}
            </fieldset>
          )}
          {errors.recordType && <p className={styles.error}>{errors.recordType}</p>}
          <div className={styles.actions}>
            <button type="button" className="button-secondary" onClick={onCancel}>
              キャンセル
            </button>
            <button type="submit" className="button-primary" disabled={saving}>
              {editing ? '保存する' : '追加する'}
            </button>
          </div>
        </form>
      </dialog>
    </div>
  )
}
