import { useQuery } from '@tanstack/react-query'
import { useDeferredValue, useEffect, useRef, useState } from 'react'
import { errorMessage, fetchRecentExercises, searchExercises } from '../lib/api/client'
import type { Exercise } from '../lib/api/types'
import { bodyPartLabel } from '../lib/format/workout'
import styles from './ExercisePicker.module.css'

/** 部位の絞り込み（A-30 の並び順と同じ） */
const BODY_PARTS = ['chest', 'back', 'legs', 'shoulders', 'arms', 'abs', 'full_body', 'cardio']

interface Props {
  onSelect: (exercise: Exercise) => void
  onClose: () => void
}

/**
 * 種目の選択（docs/screens.md S-13「種目の選択」）。作成の画面の上に全画面で開く。
 * 名前と部位で絞り込みがないときは、一番上に最近使った種目（A-31）を出す。
 * 自分専用の種目の追加・変更・削除は 9-3 で足す
 */
export function ExercisePicker({ onSelect, onClose }: Props) {
  const [q, setQ] = useState('')
  const [bodyPart, setBodyPart] = useState<string | null>(null)
  // 打つたびに検索し直すと、前の結果がちらつくので、入力の反映を少し後回しにする
  const deferredQ = useDeferredValue(q.trim())
  const searchRef = useRef<HTMLInputElement>(null)

  const recent = useQuery({ queryKey: ['exercises', 'recent'], queryFn: fetchRecentExercises })
  const results = useQuery({
    queryKey: ['exercises', 'search', deferredQ, bodyPart],
    queryFn: () => searchExercises({ q: deferredQ, bodyPart }),
  })

  useEffect(() => {
    searchRef.current?.focus()
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const filtering = deferredQ !== '' || bodyPart !== null

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

      <div className={styles.list}>
        {!filtering && recent.data && recent.data.length > 0 && (
          <section aria-label="最近使った種目">
            <h3 className={styles.group}>最近使った種目</h3>
            {recent.data.map((exercise) => (
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
        ) : !results.data ? (
          <p className={styles.message}>読み込み中…</p>
        ) : results.data.length === 0 ? (
          <p className={styles.message}>見つかりませんでした</p>
        ) : (
          groupByBodyPart(results.data).map(([part, exercises]) => (
            <section key={part} aria-label={bodyPartLabel(part)}>
              <h3 className={styles.group}>{bodyPartLabel(part)}</h3>
              {exercises.map((exercise) => (
                <button
                  key={exercise.id}
                  type="button"
                  className={styles.item}
                  onClick={() => onSelect(exercise)}
                >
                  {exercise.name}
                  {exercise.custom && <span className={styles.custom}>★自分専用</span>}
                </button>
              ))}
            </section>
          ))
        )}
      </div>
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
