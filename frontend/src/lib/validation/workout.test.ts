import { describe, expect, test } from 'vitest'
import type { Workout } from '../api/types'
import {
  type ExerciseForm,
  formFromWorkout,
  parseDuration,
  toWorkoutInput,
  type WorkoutForm,
  workoutFormSchema,
} from './workout'

// 記録の入力のチェック（docs/features.md「記録の項目」。サーバーの WorkoutInputValidator・SetRules と同じ決まりと文言）

const TODAY = '2026-11-22'
const KG = { weightUnit: 'kg', distanceUnit: 'km' } as const
const LB = { weightUnit: 'lb', distanceUnit: 'mi' } as const

function exercise(
  recordType: ExerciseForm['recordType'],
  ...sets: Partial<ExerciseForm['sets'][0]>[]
): ExerciseForm {
  return {
    exerciseId: recordType === 'weight_reps' ? 1 : recordType === 'reps' ? 8 : 48,
    name: '種目',
    bodyPart: 'chest',
    recordType,
    isRunning: recordType === 'time_distance',
    sets: sets.map((set) => ({ weight: '', reps: '', duration: '', distance: '', ...set })),
  }
}

function form(values: Partial<WorkoutForm> = {}): WorkoutForm {
  return {
    performedOn: TODAY,
    durationMin: '',
    body: '',
    visibility: 'public',
    exercises: [exercise('reps', { reps: '20' })],
    ...values,
  }
}

/** エラーの場所（exercises.0.sets.1.weight の形）と文言 */
function errors(value: WorkoutForm, units: typeof KG | typeof LB = KG): Record<string, string> {
  const result = workoutFormSchema(units, TODAY).safeParse(value)
  if (result.success) return {}
  return Object.fromEntries(
    result.error.issues.map((issue) => [issue.path.join('.'), issue.message]),
  )
}

describe('記録の項目', () => {
  test('正しい入力はエラーなし', () => {
    expect(errors(form())).toEqual({})
  })

  test('実施日は必須で、今日まで（未来は選べない）', () => {
    expect(errors(form({ performedOn: '' }))).toEqual({ performedOn: '実施日を入力してください' })
    expect(errors(form({ performedOn: '2026-11-23' }))).toEqual({
      performedOn: '未来の日付は選べません',
    })
    expect(errors(form({ performedOn: '2020-01-01' }))).toEqual({})
  })

  test('トレーニング時間は空か、1〜1440分の整数', () => {
    const message = 'トレーニング時間は1〜1440分で入力してください'
    expect(errors(form({ durationMin: '1' }))).toEqual({})
    expect(errors(form({ durationMin: '1440' }))).toEqual({})
    expect(errors(form({ durationMin: '0' }))).toEqual({ durationMin: message })
    expect(errors(form({ durationMin: '1441' }))).toEqual({ durationMin: message })
    expect(errors(form({ durationMin: '30.5' }))).toEqual({ durationMin: message })
    expect(errors(form({ durationMin: 'abc' }))).toEqual({ durationMin: message })
  })

  test('ひとことは、前後の空白を除き改行をそろえて280文字まで（絵文字も1文字）', () => {
    const message = 'ひとことは280文字以内で入力してください'
    expect(errors(form({ body: '💪'.repeat(280) }))).toEqual({})
    expect(errors(form({ body: `  ${'あ'.repeat(280)}\r\n  ` }))).toEqual({})
    expect(errors(form({ body: 'あ'.repeat(281) }))).toEqual({ body: message })
  })

  test('種目は1〜20件', () => {
    const message = '種目は1〜20件で入力してください'
    expect(errors(form({ exercises: [] }))).toEqual({ exercises: message })
    const one = exercise('reps', { reps: '1' })
    expect(errors(form({ exercises: Array(20).fill(one) }))).toEqual({})
    expect(errors(form({ exercises: Array(21).fill(one) }))).toEqual({ exercises: message })
  })

  test('セットは種目ごとに1〜20件', () => {
    const message = 'セットは1〜20件で入力してください'
    expect(errors(form({ exercises: [exercise('reps')] }))).toEqual({
      'exercises.0.sets': message,
    })
    const sets = Array(21).fill({ reps: '1' })
    expect(errors(form({ exercises: [exercise('reps', ...sets)] }))).toEqual({
      'exercises.0.sets': message,
    })
  })
})

describe('セットの値', () => {
  test('重量 × 回数（kg）：重量は 0.25〜500kg・0.25 刻み、回数は 0〜999 の整数', () => {
    const weight = '重量は0.25〜500kgの範囲で、0.25kg刻みで入力してください'
    const reps = '回数は0〜999回で入力してください'
    const check = (w: string, r: string) =>
      errors(form({ exercises: [exercise('weight_reps', { weight: w, reps: r })] }))
    expect(check('0.25', '0')).toEqual({})
    expect(check('500', '999')).toEqual({})
    expect(check('62.75', '10')).toEqual({})
    expect(check('0', '10')).toEqual({ 'exercises.0.sets.0.weight': weight })
    expect(check('500.25', '10')).toEqual({ 'exercises.0.sets.0.weight': weight })
    expect(check('60.1', '10')).toEqual({ 'exercises.0.sets.0.weight': weight })
    expect(check('', '10')).toEqual({ 'exercises.0.sets.0.weight': weight })
    expect(check('60', '1000')).toEqual({ 'exercises.0.sets.0.reps': reps })
    expect(check('60', '1.5')).toEqual({ 'exercises.0.sets.0.reps': reps })
    expect(check('60', '')).toEqual({ 'exercises.0.sets.0.reps': reps })
  })

  test('重量 × 回数（lb）：重量は 0.5〜1100lb・0.5 刻み', () => {
    const weight = '重量は0.5〜1100lbの範囲で、0.5lb刻みで入力してください'
    const check = (w: string) =>
      errors(form({ exercises: [exercise('weight_reps', { weight: w, reps: '5' })] }), LB)
    expect(check('0.5')).toEqual({})
    expect(check('1100')).toEqual({})
    expect(check('135.5')).toEqual({})
    expect(check('0.25')).toEqual({ 'exercises.0.sets.0.weight': weight })
    expect(check('1100.5')).toEqual({ 'exercises.0.sets.0.weight': weight })
  })

  test('回数のみ：1〜9999 の整数', () => {
    const reps = '回数は1〜9999回で入力してください'
    const check = (r: string) => errors(form({ exercises: [exercise('reps', { reps: r })] }))
    expect(check('1')).toEqual({})
    expect(check('9999')).toEqual({})
    expect(check('0')).toEqual({ 'exercises.0.sets.0.reps': reps })
    expect(check('10000')).toEqual({ 'exercises.0.sets.0.reps': reps })
  })

  test('時間・距離（km）：時間は1秒〜24時間、距離は空か 0.01〜500km・小数第2位まで', () => {
    const duration = '時間は1秒〜24時間で入力してください'
    const distance = '距離は0.01〜500kmの範囲で、小数第2位まで入力してください'
    const check = (d: string, km: string) =>
      errors(form({ exercises: [exercise('time_distance', { duration: d, distance: km })] }))
    expect(check('0:01', '')).toEqual({})
    expect(check('24:00:00', '500')).toEqual({})
    expect(check('30:00', '0.01')).toEqual({})
    expect(check('0:00', '')).toEqual({ 'exercises.0.sets.0.duration': duration })
    expect(check('24:00:01', '')).toEqual({ 'exercises.0.sets.0.duration': duration })
    expect(check('', '5')).toEqual({ 'exercises.0.sets.0.duration': duration })
    expect(check('30:00', '0')).toEqual({ 'exercises.0.sets.0.distance': distance })
    expect(check('30:00', '500.01')).toEqual({ 'exercises.0.sets.0.distance': distance })
    expect(check('30:00', '5.001')).toEqual({ 'exercises.0.sets.0.distance': distance })
  })

  test('時間・距離（マイル）：距離は 0.01〜310.68 マイル', () => {
    const distance = '距離は0.01〜310.68マイルの範囲で、小数第2位まで入力してください'
    const check = (mi: string) =>
      errors(
        form({ exercises: [exercise('time_distance', { duration: '30:00', distance: mi })] }),
        LB,
      )
    expect(check('310.68')).toEqual({})
    expect(check('310.69')).toEqual({ 'exercises.0.sets.0.distance': distance })
  })
})

describe('時間の読み取り', () => {
  test('「時:分:秒」と「分:秒」を秒にする', () => {
    expect(parseDuration('0:30:00')).toBe(1800)
    expect(parseDuration('1:05:05')).toBe(3905)
    expect(parseDuration('30:00')).toBe(1800)
    expect(parseDuration('0:01')).toBe(1)
    expect(parseDuration(' 5:30 ')).toBe(330)
  })

  test('形が違えば null（分・秒は 0〜59、数字以外は不可）', () => {
    expect(parseDuration('')).toBeNull()
    expect(parseDuration('30')).toBeNull()
    expect(parseDuration('1:60')).toBeNull()
    expect(parseDuration('1:00:60')).toBeNull()
    expect(parseDuration('a:00')).toBeNull()
    expect(parseDuration('1:2:3:4')).toBeNull()
  })
})

describe('API に送る形', () => {
  test('数にし、記録方式に合う値だけを送る（単位は入力した単位のまま）', () => {
    const input = toWorkoutInput(
      form({
        durationMin: '75',
        body: '  脚の日  ',
        visibility: 'followers',
        exercises: [
          exercise('weight_reps', { weight: '135', reps: '10', duration: '9:99', distance: '1' }),
          exercise('reps', { reps: '20' }),
          exercise('time_distance', { duration: '30:00', distance: '3.1' }, { duration: '10:00' }),
        ],
      }),
      LB,
    )

    expect(input).toEqual({
      performedOn: TODAY,
      body: '  脚の日  ',
      durationMin: 75,
      visibility: 'followers',
      weightUnit: 'lb',
      distanceUnit: 'mi',
      exercises: [
        { exerciseId: 1, sets: [{ weight: 135, reps: 10 }] },
        { exerciseId: 8, sets: [{ reps: 20 }] },
        { exerciseId: 48, sets: [{ durationSec: 1800, distance: 3.1 }, { durationSec: 600 }] },
      ],
    })
  })

  test('トレーニング時間が空なら null', () => {
    expect(toWorkoutInput(form(), KG).durationMin).toBeNull()
  })
})

describe('編集で開くときの入力の値', () => {
  const WORKOUT = {
    performedOn: '2026-11-20',
    body: '脚の日',
    durationMin: 75,
    visibility: 'private',
    exercises: [
      {
        exerciseId: 1,
        name: 'ベンチプレス',
        bodyPart: 'chest',
        recordType: 'weight_reps',
        isRunning: false,
        personalBest: false,
        sets: [
          { weightKg: 61.235, reps: 10, durationSec: null, distanceKm: null, personalBest: false },
        ],
      },
      {
        exerciseId: 48,
        name: 'ランニング',
        bodyPart: 'cardio',
        recordType: 'time_distance',
        isRunning: true,
        personalBest: false,
        sets: [
          { weightKg: null, reps: null, durationSec: 3905, distanceKm: 4.989, personalBest: false },
          { weightKg: null, reps: null, durationSec: 600, distanceKm: null, personalBest: false },
        ],
      },
    ],
  } as unknown as Workout

  test('ユーザーの単位にし、表示と同じ桁に丸める', () => {
    expect(formFromWorkout(WORKOUT, LB)).toEqual({
      performedOn: '2026-11-20',
      durationMin: '75',
      body: '脚の日',
      visibility: 'private',
      exercises: [
        {
          exerciseId: 1,
          name: 'ベンチプレス',
          bodyPart: 'chest',
          recordType: 'weight_reps',
          isRunning: false,
          sets: [{ weight: '135', reps: '10', duration: '', distance: '' }],
        },
        {
          exerciseId: 48,
          name: 'ランニング',
          bodyPart: 'cardio',
          recordType: 'time_distance',
          isRunning: true,
          sets: [
            { weight: '', reps: '', duration: '1:05:05', distance: '3.10' },
            { weight: '', reps: '', duration: '10:00', distance: '' },
          ],
        },
      ],
    })
    expect(formFromWorkout(WORKOUT, KG).exercises[0].sets[0].weight).toBe('61.25')
    expect(formFromWorkout(WORKOUT, KG).exercises[1].sets[0].distance).toBe('4.99')
  })

  test('トレーニング時間がなければ空', () => {
    expect(formFromWorkout({ ...WORKOUT, durationMin: null }, KG).durationMin).toBe('')
  })
})
