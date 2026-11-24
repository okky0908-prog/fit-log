import { describe, expect, test } from 'vitest'
import { BIO_MAX_LENGTH, validateProfile } from './profile'

// プロフィールの編集の入力（docs/screens.md S-41、docs/test-plan.md 5.1）。決まりと文言はサーバー（ProfileRules）と同じ

describe('表示名', () => {
  test.each([
    [0, true],
    [1, false],
    [50, false],
    [51, true],
  ])('%i 文字 → 誤り %s', (length, error) => {
    expect('displayName' in validateProfile({ displayName: 'あ'.repeat(length), bio: '' })).toBe(
      error,
    )
  })

  test('前後の空白を除いて数え、空白だけは誤り。絵文字も1文字', () => {
    expect(validateProfile({ displayName: '　 ', bio: '' })).toEqual({
      displayName: '表示名は1〜50文字で入力してください',
    })
    expect(validateProfile({ displayName: ` ${'💪'.repeat(50)} `, bio: '' })).toEqual({})
  })
})

describe('自己紹介', () => {
  test.each([
    [0, false],
    [160, false],
    [161, true],
  ])('%i 文字 → 誤り %s', (length, error) => {
    expect('bio' in validateProfile({ displayName: '山田', bio: 'あ'.repeat(length) })).toBe(error)
  })

  test('改行は CRLF を1文字にそろえてから数える', () => {
    expect(validateProfile({ displayName: '山田', bio: 'あ\r\n'.repeat(80) })).toEqual({})
    expect(validateProfile({ displayName: '山田', bio: 'あ'.repeat(161) }).bio).toBe(
      `自己紹介は${BIO_MAX_LENGTH}文字までで入力してください`,
    )
  })
})
