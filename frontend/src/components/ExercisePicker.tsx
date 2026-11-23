import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useDeferredValue, useEffect, useRef, useState } from 'react'
import {
  deleteExercise,
  errorMessage,
  fetchRecentExercises,
  searchExercises,
} from '../lib/api/client'
import type { Exercise, RecordType } from '../lib/api/types'
import { bodyPartLabel } from '../lib/format/workout'
import { ConfirmDialog } from './ConfirmDialog'
import { CustomExerciseForm } from './CustomExerciseForm'
import styles from './ExercisePicker.module.css'

/** 部位の絞り込み（A-30 の並び順と同じ） */
const BODY_PARTS = ['chest', 'back', 'legs', 'shoulders', 'arms', 'abs', 'full_body', 'cardio']

interface Props {
  onSelect: (exercise: Exercise) => void
  onClose: () => void
  /** 「種目を変える」とき：この記録方式の種目だけを出す（セットの値をそのまま使えるように） */
  recordType?: RecordType
  /** 自分専用の種目の名前・部位を変えたとき（入力中の記録の表示を合わせるため） */
  onUpdated?: (exercise: Exercise) => void
}

/**
 * 種目の選択（docs/screens.md S-13「種目の選択」）。作成の画面の上に全画面で開く。
 * 名前と部位で絞り込みがないときは、一番上に最近使った種目（A-31）を出す。
 * 下の「＋ 自分専用の種目を追加」で追加し（そのまま選ぶ）、自分専用の種目の行のメニュー（右クリックでも開く）で名前・部位の変更と削除をする
 */
export function ExercisePicker({ onSelect, onClose, recordType, onUpdated }: Props) {
  const [q, setQ] = useState('')
  const [bodyPart, setBodyPart] = useState<string | null>(null)
  // 打つたびに検索し直すと、前の結果がちらつくので、入力の反映を少し後回しにする
  const deferredQ = useDeferredValue(q.trim())
  const searchRef = useRef<HTMLInputElement>(null)
  const queryClient = useQueryClient()
  const [form, setForm] = useState<{ mode: 'add' } | { mode: 'edit'; exercise: Exercise } | null>(
    null,
  )
  const [menuFor, setMenuFor] = useState<number | null>(null)
  const [deleting, setDeleting] = useState<Exercise | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  // 入力の画面・確認ダイアログが開いている間は、Esc でそちらだけを閉じる（種目の選択は閉じない）
  const childOpen = form !== null || deleting !== null

  const recent = useQuery({ queryKey: ['exercises', 'recent'], queryFn: fetchRecentExercises })
  const results = useQuery({
    queryKey: ['exercises', 'search', deferredQ, bodyPart],
    queryFn: () => searchExercises({ q: deferredQ, bodyPart }),
  })

  useEffect(() => {
    searchRef.current?.focus()
  }, [])

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape' || childOpen) return
      if (menuFor !== null) {
        setMenuFor(null)
      } else {
        onClose()
      }
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose, childOpen, menuFor])

  /** 種目を追加・変更・削除したら、検索の結果と最近使った種目を取り直す */
  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['exercises'] })
  }

  async function remove(exercise: Exercise) {
    try {
      await deleteExercise(exercise.id)
      setMessage(null)
      refresh()
    } catch (err) {
      // 記録で使っている種目（EXERCISE_IN_USE）など
      setMessage(errorMessage(err))
    }
    setDeleting(null)
  }

  const filtering = deferredQ !== '' || bodyPart !== null
  const usable = (exercises: Exercise[]) =>
    recordType ? exercises.filter((exercise) => exercise.recordType === recordType) : exercises
  const recentExercises = usable(recent.data ?? [])
  const found = results.data && usable(results.data)

  return (
    <dialog open className={styles.picker} aria-label="種目を選ぶ">
      <div className={styles.head}>
        <button type="button" className={styles.back} aria-label="戻る" onClick={onClose}>
          ←
        </button>
        <h2 className={styles.title}>種目を選ぶ</h2>
      </div>
      <input
        ref={searchRef}
        type="search"
        className={styles.search}
        aria-label="種目を検索"
        placeholder="🔍 種目を検索"
        maxLength={30}
        value={q}
        onChange={(event) => setQ(event.target.value)}
      />
      <div className={styles.filters}>
        {[null, ...BODY_PARTS].map((part) => (
          <button
            key={part ?? 'all'}
            type="button"
            className={styles.filter}
            aria-pressed={bodyPart === part}
            onClick={() => setBodyPart(part)}
          >
            {part === null ? 'すべて' : bodyPartLabel(part)}
          </button>
        ))}
      </div>

      {recordType && <p className={styles.note}>記録方式が同じ種目だけを出しています</p>}
      {message && <p className={styles.alert}>{message}</p>}
      <div className={styles.list}>
        {!filtering && recentExercises.length > 0 && (
          <section aria-label="最近使った種目">
            <h3 className={styles.group}>最近使った種目</h3>
            {recentExercises.map((exercise) => (
              <button
                key={exercise.id}
                type="button"
                className={styles.item}
                onClick={() => onSelect(exercise)}
              >
                {exercise.name}（{bodyPartLabel(exercise.bodyPart)}）
              </button>
            ))}
          </section>
        )}
        {results.error ? (
          <p className={styles.message}>{errorMessage(results.error)}</p>
        ) : !found ? (
          <p className={styles.message}>読み込み中…</p>
        ) : found.length === 0 ? (
          <p className={styles.message}>見つかりませんでした</p>
        ) : (
          groupByBodyPart(found).map(([part, exercises]) => (
            <section key={part} aria-label={bodyPartLabel(part)}>
              <h3 className={styles.group}>{bodyPartLabel(part)}</h3>
              {exercises.map((exercise) =>
                exercise.custom ? (
                  <div key={exercise.id} className={styles.customRow}>
                    <button
                      type="button"
                      className={styles.item}
                      onClick={() => onSelect(exercise)}
                      onContextMenu={(event) => {
                        // 右クリック（スマートフォンでは長押し）でメニューを開く
                        event.preventDefault()
                        setMenuFor(exercise.id)
                      }}
                    >
                      {exercise.name}
                      <span className={styles.custom}>★自分専用</span>
                    </button>
                    <button
                      type="button"
                      className={styles.menuButton}
                      aria-label={`${exercise.name}のメニュー`}
                      aria-haspopup="menu"
                      aria-expanded={menuFor === exercise.id}
                      onClick={() => setMenuFor(menuFor === exercise.id ? null : exercise.id)}
                    >
                      …
                    </button>
                    {menuFor === exercise.id && (
                      <ul role="menu" className={styles.menu}>
                        <li role="none">
                          <button
                            type="button"
                            role="menuitem"
                            onClick={() => {
                              setMenuFor(null)
                              setForm({ mode: 'edit', exercise })
                            }}
                          >
                            名前・部位を変える
                          </button>
                        </li>
                        <li role="none">
                          <button
                            type="button"
                            role="menuitem"
                            className={styles.danger}
                            onClick={() => {
                              setMenuFor(null)
                              setDeleting(exercise)
                            }}
                          >
                            削除
                          </button>
                        </li>
                      </ul>
                    )}
                  </div>
                ) : (
                  <button
                    key={exercise.id}
                    type="button"
                    className={styles.item}
                    onClick={() => onSelect(exercise)}
                  >
                    {exercise.name}
                  </button>
                ),
              )}
            </section>
          ))
        )}
      </div>
      <div className={styles.foot}>
        <span>見つからないとき</span>
        <button type="button" className={styles.addCustom} onClick={() => setForm({ mode: 'add' })}>
          ＋ 自分専用の種目を追加
        </button>
      </div>

      {form?.mode === 'add' && (
        <CustomExerciseForm
          mode="add"
          initialName={q.trim()}
          fixedRecordType={recordType}
          onCancel={() => setForm(null)}
          onSaved={(created) => {
            refresh()
            setForm(null)
            onSelect(created)
          }}
        />
      )}
      {form?.mode === 'edit' && (
        <CustomExerciseForm
          mode="edit"
          exercise={form.exercise}
          onCancel={() => setForm(null)}
          onSaved={(updated) => {
            refresh()
            setForm(null)
            onUpdated?.(updated)
          }}
        />
      )}
      {deleting && (
        <ConfirmDialog
          title={`「${deleting.name}」を削除しますか？`}
          description="テンプレートからも外れます。記録で使っている種目は削除できません"
          confirmLabel="削除する"
          onConfirm={() => void remove(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </dialog>
  )
}

/** 部位ごとにまとめる。API が部位の順に返すので、並び順は変えない */
function groupByBodyPart(exercises: Exercise[]): [string, Exercise[]][] {
  const groups: [string, Exercise[]][] = []
  for (const exercise of exercises) {
    const last = groups.at(-1)
    if (last && last[0] === exercise.bodyPart) {
      last[1].push(exercise)
    } else {
      groups.push([exercise.bodyPart, [exercise]])
    }
  }
  return groups
}
