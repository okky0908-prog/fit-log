import { z } from 'zod'
import type { RecordType, Visibility, Workout } from '../api/types'
import { distanceValue, formatDuration, weightValue } from '../format/units'
import type { Units } from '../format/workout'

/*
 * 記録の作成・編集の入力（docs/screens.md S-13）と、そのチェック（docs/features.md「記録の項目」）。
 * 決まりと文言は、サーバー（WorkoutInputValidator・SetRules）と同じにする。サーバーでも必ず確かめる（画面のチェックは使いやすさのため）
 *
 * 入力欄の値は、打ちかけの値（「60.」など）を残せるよう、すべて文字で持つ。API に送るときに数にする
 */

/** セットの入力。記録方式に合わない欄は使わない（空のまま） */
export interface SetForm {
  weight: string
  reps: string
  /** 「時:分:秒」か「分:秒」 */
  duration: string
  distance: string
}

/** 種目の入力。名前・部位・記録方式は、種目を選んだときに入れて、表示とチェックに使う */
export interface ExerciseForm {
  exerciseId: number
  name: string
  bodyPart: string
  recordType: RecordType
  isRunning: boolean
  sets: SetForm[]
}

export interface WorkoutForm {
  performedOn: string
  durationMin: string
  body: string
  visibility: Visibility
  exercises: ExerciseForm[]
}

const MAX_EXERCISES = 20
const MAX_SETS = 20
const BODY_MAX_LENGTH = 280
const MAX_DURATION_SEC = 24 * 60 * 60

const INTEGER = /^\d+$/
const DECIMAL = /^\d+(\.\d+)?$/
/** 距離は小数第2位まで */
const DISTANCE = /^\d+(\.\d{1,2})?$/

const WEIGHT_RULES = {
  kg: { min: 0.25, max: 500, step: 0.25 },
  lb: { min: 0.5, max: 1100, step: 0.5 },
} as const

/** マイルの上限は、km にして 500km になる値（サーバーと同じ） */
const DISTANCE_MAX = { km: 500, mi: 310.68 } as const

const MESSAGES = {
  performedOnRequired: '実施日を入力してください',
  performedOnFuture: '未来の日付は選べません',
  durationMin: 'トレーニング時間は1〜1440分で入力してください',
  body: `ひとことは${BODY_MAX_LENGTH}文字以内で入力してください`,
  exercises: `種目は1〜${MAX_EXERCISES}件で入力してください`,
  sets: `セットは1〜${MAX_SETS}件で入力してください`,
  weight: {
    kg: '重量は0.25〜500kgの範囲で、0.25kg刻みで入力してください',
    lb: '重量は0.5〜1100lbの範囲で、0.5lb刻みで入力してください',
  },
  weightReps: '回数は0〜999回で入力してください',
  repsOnly: '回数は1〜9999回で入力してください',
  duration: '時間は1秒〜24時間で入力してください',
  distance: {
    km: '距離は0.01〜500kmの範囲で、小数第2位まで入力してください',
    mi: '距離は0.01〜310.68マイルの範囲で、小数第2位まで入力してください',
  },
} as const

/** ひとことを、保存する形にそろえる（サーバーの WorkoutRules.normalizeBody と同じ）。改行を LF にそろえ、前後の空白・改行を除く */
export function normalizeBody(body: string): string {
  return body.replace(/\r\n/g, '\n').trim()
}

/** 文字数。絵文字などの2つの符号で表す文字も1文字と数える（サーバーと同じく、コードポイントで数える） */
export function countCharacters(text: string): number {
  return [...text].length
}

/** 「時:分:秒」「分:秒」を秒にする。形が違えば null（分・秒は 0〜59） */
export function parseDuration(text: string): number | null {
  const parts = text.trim().split(':')
  if (parts.length < 2 || parts.length > 3 || !parts.every((part) => INTEGER.test(part))) {
    return null
  }
  const [seconds, minutes, hours = 0] = parts.map(Number).reverse()
  if (seconds > 59 || (parts.length === 3 && minutes > 59)) {
    return null
  }
  return hours * 3600 + minutes * 60 + seconds
}

function integerBetween(text: string, min: number, max: number): boolean {
  return INTEGER.test(text) && Number(text) >= min && Number(text) <= max
}

/** 刻みの倍数か（0.25 などの小数の刻みを、2進数の誤差なしに確かめる） */
function isMultipleOf(value: number, step: number): boolean {
  const steps = value / step
  return Math.abs(steps - Math.round(steps)) < 1e-9
}

/**
 * 入力のチェック。重量・距離の決まりは単位で変わり、未来の日付の判定は今日（日本時間）で決まるので、それを受け取って作る
 *
 * @param today 日本時間の今日（YYYY-MM-DD）
 */
export function workoutFormSchema(units: Units, today: string) {
  return z
    .object({
      performedOn: z.string(),
      durationMin: z.string(),
      body: z.string(),
      visibility: z.enum(['public', 'followers', 'private']),
      exercises: z.array(z.any()),
    })
    .superRefine((form, ctx) => {
      const issue = (path: (string | number)[], message: string) =>
        ctx.addIssue({ code: 'custom', path, message })

      if (form.performedOn === '') {
        issue(['performedOn'], MESSAGES.performedOnRequired)
      } else if (form.performedOn > today) {
        issue(['performedOn'], MESSAGES.performedOnFuture)
      }
      if (form.durationMin !== '' && !integerBetween(form.durationMin, 1, 1440)) {
        issue(['durationMin'], MESSAGES.durationMin)
      }
      if (countCharacters(normalizeBody(form.body)) > BODY_MAX_LENGTH) {
        issue(['body'], MESSAGES.body)
      }
      const exercises = form.exercises as ExerciseForm[]
      if (exercises.length === 0 || exercises.length > MAX_EXERCISES) {
        issue(['exercises'], MESSAGES.exercises)
      }
      exercises.forEach((exercise, i) => {
        if (exercise.sets.length === 0 || exercise.sets.length > MAX_SETS) {
          issue(['exercises', i, 'sets'], MESSAGES.sets)
        }
        exercise.sets.forEach((set, j) => {
          for (const [field, message] of setProblems(set, exercise.recordType, units)) {
            issue(['exercises', i, 'sets', j, field], message)
          }
        })
      })
    })
}

/** セットの値の問題（欄と文言）。記録方式に合う欄だけを見る */
function setProblems(set: SetForm, recordType: RecordType, units: Units): [string, string][] {
  const problems: [string, string][] = []
  if (recordType === 'weight_reps') {
    const rule = WEIGHT_RULES[units.weightUnit]
    const weight = Number(set.weight)
    if (
      !DECIMAL.test(set.weight) ||
      weight < rule.min ||
      weight > rule.max ||
      !isMultipleOf(weight, rule.step)
    ) {
      problems.push(['weight', MESSAGES.weight[units.weightUnit]])
    }
    if (!integerBetween(set.reps, 0, 999)) {
      problems.push(['reps', MESSAGES.weightReps])
    }
  } else if (recordType === 'reps') {
    if (!integerBetween(set.reps, 1, 9999)) {
      problems.push(['reps', MESSAGES.repsOnly])
    }
  } else {
    const seconds = parseDuration(set.duration)
    if (seconds === null || seconds < 1 || seconds > MAX_DURATION_SEC) {
      problems.push(['duration', MESSAGES.duration])
    }
    const distance = Number(set.distance)
    if (
      set.distance !== '' &&
      (!DISTANCE.test(set.distance) ||
        distance < 0.01 ||
        distance > DISTANCE_MAX[units.distanceUnit])
    ) {
      problems.push(['distance', MESSAGES.distance[units.distanceUnit]])
    }
  }
  return problems
}

/** API（A-20・A-22）に送るセット。記録方式に合う値だけを入れる */
export type SetInput =
  { weight: number; reps: number } | { reps: number } | { durationSec: number; distance?: number }

/** API（A-20・A-22）の data のパート。値は入力した単位のまま送り、サーバーが kg・km にする */
export interface WorkoutInput {
  performedOn: string
  body: string
  durationMin: number | null
  visibility: Visibility
  weightUnit: Units['weightUnit']
  distanceUnit: Units['distanceUnit']
  exercises: { exerciseId: number; sets: SetInput[] }[]
}

/** チェックを通った入力を、API に送る形にする */
export function toWorkoutInput(form: WorkoutForm, units: Units): WorkoutInput {
  return {
    performedOn: form.performedOn,
    body: form.body,
    durationMin: form.durationMin === '' ? null : Number(form.durationMin),
    visibility: form.visibility,
    weightUnit: units.weightUnit,
    distanceUnit: units.distanceUnit,
    exercises: form.exercises.map((exercise) => ({
      exerciseId: exercise.exerciseId,
      sets: exercise.sets.map((set) => toSetInput(set, exercise.recordType)),
    })),
  }
}

function toSetInput(set: SetForm, recordType: RecordType): SetInput {
  if (recordType === 'weight_reps') {
    return { weight: Number(set.weight), reps: Number(set.reps) }
  }
  if (recordType === 'reps') {
    return { reps: Number(set.reps) }
  }
  const durationSec = parseDuration(set.duration) as number
  return set.distance === '' ? { durationSec } : { durationSec, distance: Number(set.distance) }
}

/**
 * 編集で開くときの入力の値。重量・距離はユーザーの単位にし、表示と同じ桁に丸める
 * （kg で 61.235kg（135lb）の記録は「61.25」になる。入力は 0.25kg 刻みなので、保存し直すと 61.25kg になる）
 */
export function formFromWorkout(workout: Workout, units: Units): WorkoutForm {
  return {
    performedOn: workout.performedOn,
    durationMin: workout.durationMin === null ? '' : String(workout.durationMin),
    body: workout.body,
    visibility: workout.visibility,
    exercises: workout.exercises.map((exercise) => ({
      exerciseId: exercise.exerciseId,
      name: exercise.name,
      bodyPart: exercise.bodyPart,
      recordType: exercise.recordType,
      isRunning: exercise.isRunning,
      sets: exercise.sets.map((set) => ({
        weight: set.weightKg === null ? '' : weightValue(set.weightKg, units.weightUnit),
        reps: set.reps === null ? '' : String(set.reps),
        duration: set.durationSec === null ? '' : formatDuration(set.durationSec),
        distance: set.distanceKm === null ? '' : distanceValue(set.distanceKm, units.distanceUnit),
      })),
    })),
  }
}
