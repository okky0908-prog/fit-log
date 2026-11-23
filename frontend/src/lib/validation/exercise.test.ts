import { expect, test } from 'vitest'
import { validateCustomExercise } from './exercise'

// 自分専用の種目の入力（docs/features.md「種目（マスタ）」。サーバーの ExerciseRequests と同じ決まりと文言）

const NAME = '種目の名前は1〜30文字で入力してください'

test('正しい入力はエラーなし', () => {
  expect(
    validateCustomExercise({
      name: 'ブルガリアンスクワット',
      bodyPart: 'legs',
      recordType: 'reps',
    }),
  ).toEqual({})
})

test('名前は前後の空白を除いて1〜30文字。絵文字も1文字と数える', () => {
  const check = (name: string) =>
    validateCustomExercise({ name, bodyPart: 'legs', recordType: 'reps' }).name
  expect(check('あ'.repeat(30))).toBeUndefined()
  expect(check(`  ${'あ'.repeat(30)}  `)).toBeUndefined()
  expect(check('💪'.repeat(30))).toBeUndefined()
  expect(check('あ'.repeat(31))).toBe(NAME)
  expect(check('')).toBe(NAME)
  expect(check('   ')).toBe(NAME)
})

test('部位と記録方式は、選んでいなければエラー', () => {
  expect(validateCustomExercise({ name: '種目', bodyPart: '', recordType: '' })).toEqual({
    bodyPart: '部位を選んでください',
    recordType: '記録方式を選んでください',
  })
})

test('変更（記録方式を受け取らない）では、記録方式を見ない', () => {
  expect(validateCustomExercise({ name: '種目', bodyPart: 'chest', recordType: null })).toEqual({})
})
