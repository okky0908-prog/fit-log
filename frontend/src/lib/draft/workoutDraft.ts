import type { WorkoutForm } from '../validation/workout'

/*
 * 書きかけの記録の一時保存（docs/screens.md S-13「入力中の内容の一時保存」）。
 * - 作成中の内容を、入力のたびに端末（localStorage）に保存する。画像は保存しない
 * - ユーザーごとに分けて保存する（同じ端末で別のアカウントにログインしても、ほかの人の書きかけを出さない）
 * - 保存できない端末（プライベートモード・容量いっぱいなど）では、何もしない。一時保存は便利のためのもので、なくても記録はできる
 * - 読めない中身・形の違う中身（古い版で保存したものなど）は捨てる
 */

const KEY_PREFIX = 'fitlog:workout-draft:'

const RECORD_TYPES = new Set(['weight_reps', 'reps', 'time_distance'])
const VISIBILITIES = new Set(['public', 'followers', 'private'])

function key(username: string): string {
  return `${KEY_PREFIX}${username}`
}

export function saveDraft(
  username: string,
  form: WorkoutForm,
  storage: Storage = localStorage,
): void {
  try {
    storage.setItem(key(username), JSON.stringify(form))
  } catch {
    // 保存できない端末。一時保存なしで続ける
  }
}

/** 保存した書きかけ。なければ・読めなければ null */
export function loadDraft(username: string, storage: Storage = localStorage): WorkoutForm | null {
  let text: string | null
  try {
    text = storage.getItem(key(username))
  } catch {
    return null
  }
  if (text === null) {
    return null
  }
  try {
    const value: unknown = JSON.parse(text)
    if (isWorkoutForm(value)) {
      return value
    }
  } catch {
    // 読めない中身。下で捨てる
  }
  clearDraft(username, storage)
  return null
}

export function clearDraft(username: string, storage: Storage = localStorage): void {
  try {
    storage.removeItem(key(username))
  } catch {
    // 保存できない端末。消すものもない
  }
}

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function isStrings(value: Record<string, unknown>, names: string[]): boolean {
  return names.every((name) => typeof value[name] === 'string')
}

/** 入力の形（validation/workout.ts の WorkoutForm）になっているか。値の範囲は、入力のチェックが確かめる */
function isWorkoutForm(value: unknown): value is WorkoutForm {
  return (
    isObject(value) &&
    isStrings(value, ['performedOn', 'durationMin', 'body']) &&
    VISIBILITIES.has(value.visibility as string) &&
    Array.isArray(value.exercises) &&
    value.exercises.every(
      (exercise: unknown) =>
        isObject(exercise) &&
        typeof exercise.exerciseId === 'number' &&
        isStrings(exercise, ['name', 'bodyPart']) &&
        RECORD_TYPES.has(exercise.recordType as string) &&
        typeof exercise.isRunning === 'boolean' &&
        Array.isArray(exercise.sets) &&
        exercise.sets.every(
          (set: unknown) =>
            isObject(set) && isStrings(set, ['weight', 'reps', 'duration', 'distance']),
        ),
    )
  )
}
