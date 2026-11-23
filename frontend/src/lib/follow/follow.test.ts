import { describe, expect, test } from 'vitest'
import { followButtonLabel, unfollowConfirmation } from './follow'

// フォローボタン（docs/screens.md「ユーザー一覧の行（共通）」・S-40）

describe('followButtonLabel', () => {
  test.each([
    ['none', false, 'フォローする'],
    ['none', true, 'フォローをリクエスト'],
    ['requested', true, 'リクエスト中'],
    ['following', false, 'フォロー中'],
    ['following', true, 'フォロー中'],
  ] as const)('%s・鍵アカウント %s → %s', (status, isPrivate, label) => {
    expect(followButtonLabel(status, isPrivate)).toBe(label)
  })
})

describe('unfollowConfirmation', () => {
  test('リクエスト中は取り消し、フォロー中は解除を確認する', () => {
    expect(unfollowConfirmation('requested')).toEqual({
      title: 'リクエストを取り消しますか？',
      confirmLabel: '取り消す',
    })
    expect(unfollowConfirmation('following')).toEqual({
      title: 'フォローを解除しますか？',
      confirmLabel: '解除する',
    })
  })

  test('フォローしていなければ確認しない（押したらすぐフォローする）', () => {
    expect(unfollowConfirmation('none')).toBeNull()
  })
})
