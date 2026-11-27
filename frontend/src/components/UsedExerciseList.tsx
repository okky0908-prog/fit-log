import { useId } from 'react'
import { Link } from 'react-router'
import type { UsedExercise } from '../lib/api/types'
import { type ExerciseGroup, groupByBodyPart } from '../lib/stats/trend'
import styles from './UsedExerciseList.module.css'

interface Props {
  used: UsedExercise[]
  /** 種目を選んだときの移り先 */
  hrefFor: (exerciseId: number) => string
}

/** 自分が記録した種目の一覧（部位ごと、最後に記録した日つき）。S-22 の種目の一覧と「種目を変える」で使う */
export function UsedExerciseList({ used, hrefFor }: Props) {
  if (used.length === 0) {
    return <p className={styles.empty}>まだ記録した種目がありません</p>
  }
  return (
    <>
      {groupByBodyPart(used).map((group) => (
        <Group key={group.bodyPart} group={group} hrefFor={hrefFor} />
      ))}
    </>
  )
}

function Group({ group, hrefFor }: { group: ExerciseGroup; hrefFor: Props['hrefFor'] }) {
  const headingId = useId()
  return (
    <section aria-labelledby={headingId} className={styles.group}>
      <h2 id={headingId} className={styles.heading}>
        {group.label}
      </h2>
      <ul className={styles.list}>
        {group.items.map(({ exercise, lastPerformedOn }) => (
          <li key={exercise.id}>
            <Link to={hrefFor(exercise.id)} className={styles.row}>
              <span className={styles.name}>{exercise.name}</span>
              <span className={styles.sub}>最後の記録 {lastPerformedOn.replaceAll('-', '/')}</span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
