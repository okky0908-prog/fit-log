import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import {
  type Control,
  type FieldErrors,
  type Resolver,
  useFieldArray,
  useForm,
  useFormState,
  useWatch,
  type UseFormRegister,
} from 'react-hook-form'
import { useLocation, useNavigate, useParams } from 'react-router'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ExercisePicker } from '../components/ExercisePicker'
import {
  ApiError,
  createWorkout,
  errorMessage,
  fetchWorkout,
  updateWorkout,
} from '../lib/api/client'
import type { Exercise, Workout } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { tokyoDate } from '../lib/format/date'
import { bodyPartLabel, type Units } from '../lib/format/workout'
import { useToast } from '../lib/toast'
import {
  countCharacters,
  type ExerciseForm,
  formFromWorkout,
  normalizeBody,
  type SetForm,
  toWorkoutInput,
  type WorkoutForm,
  workoutFormSchema,
} from '../lib/validation/workout'
import styles from './WorkoutEditorPage.module.css'

const BODY_MAX_LENGTH = 280
const EMPTY_SET: SetForm = { weight: '', reps: '', duration: '', distance: '' }

const VISIBILITIES = [
  { value: 'public', label: '🌐 公開' },
  { value: 'followers', label: '👥 フォロワー限定' },
  { value: 'private', label: '🔒 自分のみ' },
] as const

/** 入力の欄の名前（API の errors の field を、入力の欄に結び付けるため） */
const FORM_FIELDS =
  /^(performedOn|durationMin|body|visibility|exercises(\.\d+\.sets(\.\d+\.(weight|reps|duration|distance))?)?)$/

/**
 * S-13 記録の作成（`/workouts/new`）・編集（`/workouts/:workoutId/edit`）。docs/screens.md。
 *
 * 画像・書きかけの一時保存・自分専用の種目の追加と変更・「種目を変える」・セットの並べ替え（9-3）、
 * テンプレート・前回の記録の呼び出し（段階 4）は、それぞれの PR で足す
 */
export function WorkoutEditorPage() {
  const { workoutId } = useParams()
  if (workoutId === undefined) {
    return <WorkoutEditor />
  }
  return <EditLoader workoutId={workoutId} />
}

/** 編集：今の内容を読み込んでから開く。自分の記録でなければ開かない（サーバーも 403 にする） */
function EditLoader({ workoutId }: { workoutId: string }) {
  const id = /^\d+$/.test(workoutId) ? Number(workoutId) : null
  const query = useQuery({
    queryKey: ['workout', id],
    queryFn: () => fetchWorkout(id as number),
    enabled: id !== null,
  })
  if (id === null || (query.data && !query.data.mine) || isNotFound(query.error)) {
    return <p className={styles.message}>この記録は見つかりません</p>
  }
  if (query.error) {
    return (
      <div className={styles.message}>
        <p>{errorMessage(query.error)}</p>
        <button type="button" className="button-secondary" onClick={() => void query.refetch()}>
          もう一度読み込む
        </button>
      </div>
    )
  }
  if (!query.data) {
    return <p className={styles.message}>読み込み中…</p>
  }
  return <WorkoutEditor workout={query.data} />
}

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'WORKOUT_NOT_FOUND'
}

function WorkoutEditor({ workout }: { workout?: Workout }) {
  const { user } = useAuth()
  const units: Units = useMemo(
    () => ({ weightUnit: user?.weightUnit ?? 'kg', distanceUnit: user?.distanceUnit ?? 'km' }),
    [user?.weightUnit, user?.distanceUnit],
  )
  // 未来の日付の判定と、実施日の初期値（日本時間の今日）。開いたときの日付で決める
  const [today] = useState(() => tokyoDate(new Date()))
  const resolver = useMemo(
    () => zodResolver(workoutFormSchema(units, today)) as unknown as Resolver<WorkoutForm>,
    [units, today],
  )
  const {
    control,
    register,
    handleSubmit,
    getValues,
    setError,
    formState: { errors, isValid, isDirty, isSubmitting },
  } = useForm<WorkoutForm>({
    resolver,
    // 欄を離れたときに理由を出し、そのあとは打つたびに確かめる
    mode: 'onTouched',
    defaultValues: workout
      ? formFromWorkout(workout, units)
      : { performedOn: today, durationMin: '', body: '', visibility: 'public', exercises: [] },
  })
  const exercises = useFieldArray({ control, name: 'exercises' })
  const [picking, setPicking] = useState(false)
  const [confirmingClose, setConfirmingClose] = useState(false)
  const navigate = useNavigate()
  const goBack = useGoBack()
  const toast = useToast()
  const queryClient = useQueryClient()

  const bodyLength = countCharacters(normalizeBody(useWatch({ control, name: 'body' })))

  function addExercise(exercise: Exercise) {
    exercises.append(
      {
        exerciseId: exercise.id,
        name: exercise.name,
        bodyPart: exercise.bodyPart,
        recordType: exercise.recordType,
        isRunning: exercise.isRunning,
        sets: [{ ...EMPTY_SET }],
      },
      // 新しい欄に自動で移らない。移ると、ほかを押しただけで「触った欄」になり、まだ入力していない欄にエラーが出る
      { shouldFocus: false },
    )
    setPicking(false)
  }

  const submit = handleSubmit(async (form) => {
    const input = toWorkoutInput(form, units)
    try {
      const saved = workout
        ? await updateWorkout(workout.id, {
            ...input,
            keepImageIds: workout.images.map((image) => image.id),
          })
        : await createWorkout(input)
      queryClient.setQueryData(['workout', saved.id], saved)
      toast(savedMessage(saved, workout ? '記録を保存しました' : '記録を投稿しました'))
      // 作成・編集の画面は履歴に残さない（詳細から「戻る」で作成の画面に戻らないように）
      void navigate(`/workouts/${saved.id}`, { replace: true })
    } catch (err) {
      showFailure(err)
    }
  })

  /** 入力の欄に結び付くエラーはその欄の下に、それ以外はお知らせで出す。入力は残す */
  function showFailure(err: unknown) {
    const fieldErrors = err instanceof ApiError ? err.fieldErrors : []
    let shown = false
    for (const fieldError of fieldErrors) {
      const path = fieldError.field.replace(/\[(\d+)\]/g, '.$1')
      if (FORM_FIELDS.test(path)) {
        setError(path as never, { type: 'server', message: fieldError.message })
        shown = true
      }
    }
    if (!shown) {
      toast(errorMessage(err))
    }
  }

  return (
    <>
      <div className={styles.bar}>
        <button
          type="button"
          className={styles.close}
          aria-label="閉じる"
          onClick={() => (isDirty ? setConfirmingClose(true) : goBack())}
        >
          ×
        </button>
        <h1 className={styles.title}>{workout ? '記録を編集' : '新しい記録'}</h1>
        <button
          type="submit"
          form="workout-form"
          className="button-primary"
          disabled={!isValid || isSubmitting}
        >
          {workout ? '保存する' : '投稿する'}
        </button>
      </div>

      <form id="workout-form" className={styles.form} onSubmit={(e) => void submit(e)} noValidate>
        <div className={styles.row}>
          <label htmlFor="performedOn">実施日</label>
          <div>
            <input
              id="performedOn"
              type="date"
              max={today}
              aria-invalid={errors.performedOn ? true : undefined}
              aria-describedby={errors.performedOn ? 'performedOn-error' : undefined}
              {...register('performedOn')}
            />
            <FieldError id="performedOn-error" message={errors.performedOn?.message} />
          </div>
        </div>
        <div className={styles.row}>
          <label htmlFor="durationMin">トレーニング時間（分）</label>
          <div>
            <input
              id="durationMin"
              inputMode="numeric"
              className={styles.short}
              placeholder="任意"
              aria-invalid={errors.durationMin ? true : undefined}
              aria-describedby={errors.durationMin ? 'durationMin-error' : undefined}
              {...register('durationMin')}
            />
            <FieldError id="durationMin-error" message={errors.durationMin?.message} />
          </div>
        </div>

        {exercises.fields.map((field, index) => (
          <ExerciseFields
            key={field.id}
            index={index}
            exercise={field}
            count={exercises.fields.length}
            control={control}
            register={register}
            errors={errors}
            units={units}
            getSets={() => getValues(`exercises.${index}.sets`)}
            onMove={(to) => exercises.move(index, to)}
            onRemove={() => exercises.remove(index)}
          />
        ))}
        <FieldError message={arrayError(errors.exercises)} />
        <button type="button" className={styles.add} onClick={() => setPicking(true)}>
          ＋ 種目を追加
        </button>

        <div className={styles.field}>
          <label htmlFor="body">ひとこと</label>
          <textarea
            id="body"
            rows={3}
            aria-invalid={errors.body ? true : undefined}
            aria-describedby="body-count"
            {...register('body')}
          />
          <p
            id="body-count"
            className={bodyLength > BODY_MAX_LENGTH ? styles.countOver : styles.count}
          >
            {bodyLength} / {BODY_MAX_LENGTH}
          </p>
          <FieldError message={errors.body?.message} />
        </div>

        <fieldset className={styles.visibility}>
          <legend>公開範囲</legend>
          {VISIBILITIES.map((option) => (
            <label key={option.value} className={styles.radio}>
              <input type="radio" value={option.value} {...register('visibility')} />
              {option.label}
              {option.value === 'public' && user?.isPrivate && (
                <span className={styles.note}>
                  鍵アカウントなので、承認したフォロワーだけに見えます
                </span>
              )}
            </label>
          ))}
        </fieldset>
      </form>

      {picking && <ExercisePicker onSelect={addExercise} onClose={() => setPicking(false)} />}
      {confirmingClose && (
        <ConfirmDialog
          title="入力中の記録は保存されません。閉じますか？"
          confirmLabel="閉じる（入力を捨てる）"
          onConfirm={goBack}
          onCancel={() => setConfirmingClose(false)}
        />
      )}
    </>
  )
}

/** 保存したあとのお知らせ。自己ベストを更新した種目があれば、それを伝える（docs/screens.md S-13「投稿する」） */
function savedMessage(saved: Workout, fallback: string): string {
  const best = saved.exercises.find((exercise) => exercise.personalBest)
  return best ? `🏆 ${best.name}の自己ベストを更新しました！` : fallback
}

/** 前の画面へ戻る。URL を直接開いたなど、アプリの中に戻る先がなければホームへ */
function useGoBack(): () => void {
  const navigate = useNavigate()
  const location = useLocation()
  return () => void (location.key === 'default' ? navigate('/') : navigate(-1))
}

/** 一覧そのもののエラー（「種目は1〜20件」など）。React Hook Form は一覧の欄のエラーを root に入れることがある */
function arrayError(error: unknown): string | undefined {
  const value = error as { message?: string; root?: { message?: string } } | undefined
  return value?.message ?? value?.root?.message
}

function FieldError({ id, message }: { id?: string; message?: string }) {
  return message ? (
    <p id={id} className={styles.error}>
      {message}
    </p>
  ) : null
}

interface ExerciseFieldsProps {
  index: number
  exercise: ExerciseForm
  count: number
  control: Control<WorkoutForm>
  register: UseFormRegister<WorkoutForm>
  errors: FieldErrors<WorkoutForm>
  units: Units
  getSets: () => SetForm[]
  onMove: (to: number) => void
  onRemove: () => void
}

/** セットの列の見出し（記録方式で変わる） */
function columnLabels(recordType: ExerciseForm['recordType'], units: Units): string[] {
  if (recordType === 'weight_reps') return [`重量（${units.weightUnit}）`, '回数']
  if (recordType === 'reps') return ['回数']
  return ['時間', `距離（${units.distanceUnit}・任意）`]
}

/** 種目1つと、そのセットの入力（記録方式に合わせて欄を出す） */
function ExerciseFields({
  index,
  exercise,
  count,
  control,
  register,
  errors,
  units,
  getSets,
  onMove,
  onRemove,
}: ExerciseFieldsProps) {
  const sets = useFieldArray({ control, name: `exercises.${index}.sets` })
  // 種目・セットを足すと、React Hook Form は一覧全体を確かめ直し、まだ触っていない欄にもエラーを入れる。
  // 触った欄・投稿しようとしたあと・サーバーのエラーだけを出す
  const { touchedFields, isSubmitted } = useFormState({ control })
  const touchedSets = touchedFields.exercises?.[index]?.sets
  const title = `${exercise.name}（${bodyPartLabel(exercise.bodyPart)}）`
  const setErrors = errors.exercises?.[index]?.sets

  /** 直前のセットと同じ値で足す（同じ重さで何セットもするため）。セットがなければ空で足す */
  function addSet() {
    const current = getSets()
    sets.append({ ...(current.at(-1) ?? EMPTY_SET) })
  }

  function field(j: number, name: keyof SetForm, label: string, wide = false) {
    const path = `exercises.${index}.sets.${j}.${name}` as const
    const fieldError = setErrors?.[j]?.[name]
    const visible =
      isSubmitted || fieldError?.type === 'server' || touchedSets?.[j]?.[name] === true
    const error = visible ? fieldError?.message : undefined
    const errorId = `exercise-${index}-set-${j}-${name}-error`
    return (
      <div className={wide ? styles.cellWide : styles.cell}>
        <input
          aria-label={`${j + 1}セット目の${label}`}
          inputMode={name === 'duration' ? 'text' : 'decimal'}
          placeholder={name === 'duration' ? '0:30:00' : undefined}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? errorId : undefined}
          {...register(path)}
        />
        <FieldError id={errorId} message={error} />
      </div>
    )
  }

  return (
    <section className={styles.exercise} aria-label={title}>
      <div className={styles.exerciseHead}>
        <h2 className={styles.exerciseName}>■ {title}</h2>
        <div className={styles.tools}>
          <button
            type="button"
            aria-label="上へ"
            disabled={index === 0}
            onClick={() => onMove(index - 1)}
          >
            ↑
          </button>
          <button
            type="button"
            aria-label="下へ"
            disabled={index === count - 1}
            onClick={() => onMove(index + 1)}
          >
            ↓
          </button>
          <button type="button" aria-label="種目を削除" onClick={onRemove}>
            ×
          </button>
        </div>
      </div>
      {sets.fields.length > 0 && (
        // 列の見出し（入力の欄には「1セット目の重量（kg）」などの名前があるので、読み上げでは読まない）
        <div className={styles.columns} aria-hidden="true">
          <span className={styles.setNumber}>#</span>
          {columnLabels(exercise.recordType, units).map((label) => (
            <span
              key={label}
              className={exercise.recordType === 'reps' ? styles.cellWide : styles.cell}
            >
              {label}
            </span>
          ))}
          <span className={styles.removeSpace} />
        </div>
      )}
      <ol className={styles.sets}>
        {sets.fields.map((set, j) => (
          <li key={set.id} className={styles.set}>
            <span className={styles.setNumber}>{j + 1}</span>
            {exercise.recordType === 'weight_reps' && (
              <>
                {field(j, 'weight', `重量（${units.weightUnit}）`)}
                {field(j, 'reps', '回数')}
              </>
            )}
            {exercise.recordType === 'reps' && field(j, 'reps', '回数', true)}
            {exercise.recordType === 'time_distance' && (
              <>
                {field(j, 'duration', '時間')}
                {field(j, 'distance', `距離（${units.distanceUnit}）`)}
              </>
            )}
            <button
              type="button"
              className={styles.removeSet}
              aria-label={`${j + 1}セット目を削除`}
              onClick={() => sets.remove(j)}
            >
              ×
            </button>
          </li>
        ))}
      </ol>
      <FieldError message={arrayError(setErrors)} />
      <button type="button" className={styles.addSet} onClick={addSet}>
        ＋ セットを追加
      </button>
    </section>
  )
}
