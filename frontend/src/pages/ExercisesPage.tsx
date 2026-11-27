import { useQuery } from '@tanstack/react-query'
import { BackButton } from '../components/BackButton'
import { UsedExerciseList } from '../components/UsedExerciseList'
import { errorMessage, fetchUsedExercises } from '../lib/api/client'
import styles from './RecordsPage.module.css'

/** S-22 の種目の一覧（/records/exercises）。自分が記録した種目を部位ごとに出し、選ぶとその種目の推移へ */
export function ExercisesPage() {
  const query = useQuery({ queryKey: ['stats', 'exercises'], queryFn: fetchUsedExercises })
  return (
    <>
      <BackButton fallback="/records">記録</BackButton>
      <h1 className={styles.title}>種目ごとの推移</h1>
      {query.error ? (
        <div className={styles.message}>
          <p>{errorMessage(query.error)}</p>
          <button type="button" className="button-secondary" onClick={() => void query.refetch()}>
            もう一度読み込む
          </button>
        </div>
      ) : !query.data ? (
        <p className={styles.message}>読み込み中…</p>
      ) : (
        <UsedExerciseList used={query.data} hrefFor={(id) => `/records/exercises/${id}`} />
      )}
    </>
  )
}
