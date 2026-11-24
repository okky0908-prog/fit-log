import { countCharacters } from './workout'

/*
 * プロフィールの編集の入力（docs/screens.md S-41）。決まりと文言はサーバー（ProfileRules）と同じ。
 * 前後の空白を除き、改行を LF にそろえてから数える（絵文字も1文字）
 */

const DISPLAY_NAME_MAX_LENGTH = 50
export const BIO_MAX_LENGTH = 160

export interface ProfileInput {
  displayName: string
  bio: string
}

export type ProfileErrors = Partial<Record<'displayName' | 'bio', string>>

/** 自己紹介の文字数（画面の「40 / 160」にも使う） */
export function bioLength(bio: string): number {
  return countCharacters(bio.replace(/\r\n/g, '\n').trim())
}

export function validateProfile(input: ProfileInput): ProfileErrors {
  const errors: ProfileErrors = {}
  const nameLength = countCharacters(input.displayName.trim())
  if (nameLength < 1 || nameLength > DISPLAY_NAME_MAX_LENGTH) {
    errors.displayName = `表示名は1〜${DISPLAY_NAME_MAX_LENGTH}文字で入力してください`
  }
  if (bioLength(input.bio) > BIO_MAX_LENGTH) {
    errors.bio = `自己紹介は${BIO_MAX_LENGTH}文字までで入力してください`
  }
  return errors
}
