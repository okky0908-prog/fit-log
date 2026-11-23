import { countCharacters } from './workout'

/*
 * 自分専用の種目の入力（docs/features.md「種目（マスタ）」）。決まりと文言はサーバー（ExerciseRequests）と同じ。
 * 同じ名前の判定（大文字・小文字・全角半角を区別しない）と100件の上限は、サーバーだけが確かめる（409）
 */

export interface CustomExerciseInput {
  name: string
  bodyPart: string
  /** 記録方式。変更（A-33）では変えられないので null */
  recordType: string | null
}

export type CustomExerciseErrors = Partial<Record<'name' | 'bodyPart' | 'recordType', string>>

const NAME_MAX_LENGTH = 30

export function validateCustomExercise(input: CustomExerciseInput): CustomExerciseErrors {
  const errors: CustomExerciseErrors = {}
  const length = countCharacters(input.name.trim())
  if (length < 1 || length > NAME_MAX_LENGTH) {
    errors.name = `種目の名前は1〜${NAME_MAX_LENGTH}文字で入力してください`
  }
  if (input.bodyPart === '') {
    errors.bodyPart = '部位を選んでください'
  }
  if (input.recordType === '') {
    errors.recordType = '記録方式を選んでください'
  }
  return errors
}
