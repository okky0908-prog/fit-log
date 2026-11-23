import { zodResolver } from '@hookform/resolvers/zod'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
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
import { type ImageItem, MAX_IMAGES, WorkoutImagesField } from '../components/WorkoutImagesField'
import {
  ApiError,
  createWorkout,
  errorMessage,
  fetchWorkout,
  updateWorkout,
} from '../lib/api/client'
import type { Exercise, RecordType, Workout } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { clearDraft, loadDraft, saveDraft } from '../lib/draft/workoutDraft'
import { tokyoDate } from '../lib/format/date'
import { shrinkImage, UNUSABLE_IMAGE_MESSAGE, UnusableImageError } from '../lib/image/shrink'
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
 * 自分専用の種目の追加と変更（9-5）、
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
    reset,
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
  const images = useImages(workout)
  // 種目の選択：足す（add）か、index 番目の種目を記録方式が同じ種目に変える（change）
  const [picker, setPicker] = useState<
    { mode: 'add' } | { mode: 'change'; index: number; recordType: RecordType } | null
  >(null)
  const username = user?.username ?? ''
  // 書きかけの一時保存（作成だけ）。前の書きかけがあれば、続けるか破棄するかを聞くまで保存しない（上書きしないように）
  const [pendingDraft, setPendingDraft] = useState(() => (workout ? null : loadDraft(username)))
  const values = useWatch({ control })
  useEffect(() => {
    if (!workout && pendingDraft === null && isDirty) {
      saveDraft(username, getValues())
    }
  }, [values, workout, pendingDraft, isDirty, username, getValues])
  const [confirmingClose, setConfirmingClose] = useState(false)
  const navigate = useNavigate()
  const goBack = useGoBack()
  const toast = useToast()
  const queryClient = useQueryClient()

  const bodyLength = countCharacters(normalizeBody(useWatch({ control, name: 'body' })))

  function selectExercise(exercise: Exercise) {
    if (picker?.mode === 'change') {
      // セットの値はそのまま使う（記録方式が同じ種目だけを選べる）
      exercises.update(picker.index, {
        ...getValues(`exercises.${picker.index}`),
        exerciseId: exercise.id,
        name: exercise.name,
        bodyPart: exercise.bodyPart,
        isRunning: exercise.isRunning,
      })
      setPicker(null)
      return
    }
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
    setPicker(null)
  }

  const submit = handleSubmit(async (form) => {
    const input = toWorkoutInput(form, units)
    try {
      const saved = workout
        ? await updateWorkout(
            workout.id,
            { ...input, keepImageIds: images.keptIds() },
            images.newBlobs(),
          )
        : await createWorkout(input, images.newBlobs())
      queryClient.setQueryData(['workout', saved.id], saved)
      if (!workout) clearDraft(username)
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
    const imageMessages: string[] = []
    for (const fieldError of fieldErrors) {
      const path = fieldError.field.replace(/\[(\d+)\]/g, '.$1')
      if (FORM_FIELDS.test(path)) {
        setError(path as never, { type: 'server', message: fieldError.message })
        shown = true
      } else if (/^(images|keepImageIds)\b/.test(path)) {
        imageMessages.push(images.describeError(path, fieldError.message))
        shown = true
      }
    }
    if (imageMessages.length > 0) {
      images.setMessage(imageMessages.join(' '))
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
          onClick={() => (isDirty || images.changed ? setConfirmingClose(true) : goBack())}
        >
          ×
        </button>
        <h1 className={styles.title}>{workout ? '記録を編集' : '新しい記録'}</h1>
        <button
          type="submit"
          form="workout-form"
          className="button-primary"
          disabled={!isValid || isSubmitting || images.busy}
        >
          {workout ? '保存する' : '投稿する'}
        </button>
      </div>

      {pendingDraft && (
        <div className={styles.draft}>
          <p>書きかけの記録があります。続きから入力しますか？</p>
          <p className={styles.draftNote}>画像は選び直してください</p>
          <div className={styles.draftActions}>
            <button
              type="button"
              className="button-primary"
              onClick={() => {
                // 初期値は空のままにして、書きかけを「入力した内容」として入れる（閉じるときに確かめるため）
                reset(pendingDraft, { keepDefaultValues: true })
                setPendingDraft(null)
              }}
            >
              続ける
            </button>
            <button
              type="button"
              className="button-secondary"
              onClick={() => {
                clearDraft(username)
                setPendingDraft(null)
              }}
            >
              破棄する
            </button>
          </div>
        </div>
      )}

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
            onChangeExercise={() =>
              setPicker({ mode: 'change', index, recordType: field.recordType })
            }
          />
        ))}
        <FieldError message={arrayError(errors.exercises)} />
        <button type="button" className={styles.add} onClick={() => setPicker({ mode: 'add' })}>
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

        <WorkoutImagesField
          images={images.items}
          busy={images.busy}
          message={images.message}
          onAdd={(files) => void images.add(files)}
          onRemove={images.remove}
        />

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

      {picker && (
        <ExercisePicker
          onSelect={selectExercise}
          onClose={() => setPicker(null)}
          recordType={picker.mode === 'change' ? picker.recordType : undefined}
        />
      )}
      {confirmingClose && (
        <ConfirmDialog
          title="入力中の記録は保存されません。閉じますか？"
          confirmLabel="閉じる（入力を捨てる）"
          onConfirm={() => {
            if (!workout) clearDraft(username)
            goBack()
          }}
          onCancel={() => setConfirmingClose(false)}
        />
      )}
    </>
  )
}

/**
 * 画像の欄の状態。今の画像（編集）のあとに、新しく選んで縮小した画像を並べる（docs/api.md A-22 の keepImageIds・images）
 */
function useImages(workout?: Workout) {
  const [items, setItems] = useState<ImageItem[]>(() =>
    (workout?.images ?? []).map((image) => ({
      kind: 'existing',
      key: `existing-${image.id}`,
      id: image.id,
      url: image.url,
    })),
  )
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState<string | null>(null)
  const [changed, setChanged] = useState(false)
  const nextKey = useRef(0)
  // 画面を閉じたら、プレビューに使った仮の URL を捨てる（ブラウザのメモリを空ける）
  const itemsRef = useRef(items)
  useEffect(() => {
    itemsRef.current = items
  }, [items])
  useEffect(
    () => () => {
      for (const item of itemsRef.current) {
        if (item.kind === 'new') URL.revokeObjectURL(item.url)
      }
    },
    [],
  )

  async function add(files: File[]) {
    const room = MAX_IMAGES - items.length
    const messages = files.length > room ? ['画像は4枚まで選べます'] : []
    setBusy(true)
    const added: ImageItem[] = []
    for (const file of files.slice(0, room)) {
      try {
        const blob = await shrinkImage(file)
        nextKey.current += 1
        added.push({
          kind: 'new',
          key: `new-${nextKey.current}`,
          blob,
          url: URL.createObjectURL(blob),
        })
      } catch (err) {
        messages.unshift(err instanceof UnusableImageError ? err.message : UNUSABLE_IMAGE_MESSAGE)
      }
    }
    setItems((current) => [...current, ...added])
    setMessage(messages.length > 0 ? [...new Set(messages)].join(' ') : null)
    setChanged(true)
    setBusy(false)
  }

  function remove(index: number) {
    const item = items[index]
    if (item.kind === 'new') URL.revokeObjectURL(item.url)
    setItems(items.filter((_, i) => i !== index))
    setMessage(null)
    setChanged(true)
  }

  const kept = items.filter((item) => item.kind === 'existing')

  /** サーバーの画像のエラー。images[0] は新しい画像の何枚目かなので、並んでいる順の番号にする */
  function describeError(path: string, text: string): string {
    const match = /^images\.(\d+)$/.exec(path)
    return match ? `画像 ${kept.length + Number(match[1]) + 1}：${text}` : text
  }

  return {
    items,
    busy,
    message,
    changed,
    add,
    remove,
    setMessage,
    describeError,
    keptIds: () => kept.map((item) => item.id),
    newBlobs: () => items.flatMap((item) => (item.kind === 'new' ? [item.blob] : [])),
  }
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
  onChangeExercise: () => void
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
  onChangeExercise,
}: ExerciseFieldsProps) {
  // メニューを開いているセット（「上へ」「下へ」）
  const [setMenu, setSetMenu] = useState<number | null>(null)
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
            aria-label="種目を変える"
            title="種目を変える"
            onClick={onChangeExercise}
          >
            ⇄
          </button>
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
            <div className={styles.setMenu}>
              <button
                type="button"
                className={styles.removeSet}
                aria-label={`${j + 1}セット目のメニュー`}
                aria-haspopup="menu"
                aria-expanded={setMenu === j}
                onClick={() => setSetMenu(setMenu === j ? null : j)}
              >
                ⋮
              </button>
              {setMenu === j && (
                <ul role="menu" className={styles.setMenuList}>
                  {[
                    { label: '上へ', to: j - 1 },
                    { label: '下へ', to: j + 1 },
                  ].map((item) => (
                    <li key={item.label} role="none">
                      <button
                        type="button"
                        role="menuitem"
                        disabled={item.to < 0 || item.to >= sets.fields.length}
                        onClick={() => {
                          sets.move(j, item.to)
                          setSetMenu(null)
                        }}
                      >
                        {item.label}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
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
