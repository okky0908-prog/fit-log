import { describe, expect, test } from 'vitest'
import {
  canPostComment,
  COMMENT_MAX_LENGTH,
  commentLength,
  mentionToShow,
  mergeById,
  remainingReplies,
  replyPrefix,
} from './comment'

// S-12 のコメント欄の決まり（docs/screens.md S-12、docs/test-plan.md 5.1：コメントは1〜140文字）

describe('文字数と、投稿できるか', () => {
  test('前後の空白・改行を除いて数え、絵文字も1文字', () => {
    expect(commentLength('  ナイス💪\n')).toBe(4)
    expect(commentLength('')).toBe(0)
  })

  test('1〜140文字なら投稿でき、空・空白だけ・141文字はできない', () => {
    expect(COMMENT_MAX_LENGTH).toBe(140)
    expect(canPostComment('あ')).toBe(true)
    expect(canPostComment('あ'.repeat(140))).toBe(true)
    expect(canPostComment(` ${'あ'.repeat(140)}\n`)).toBe(true)
    expect(canPostComment('')).toBe(false)
    expect(canPostComment(' \n ')).toBe(false)
    expect(canPostComment('あ'.repeat(141))).toBe(false)
  })
})

test('返信への返信の入力欄は、先頭に @ユーザー名 と空白を入れておく', () => {
  expect(replyPrefix('sato')).toBe('@sato ')
})

test('返信をさらに表示の数は、まだ表示していない返信の数（0 を下回らない）', () => {
  expect(remainingReplies(5, 3)).toBe(2)
  expect(remainingReplies(3, 3)).toBe(0)
  // 表示したあとに返信が消えたときなど
  expect(remainingReplies(3, 4)).toBe(0)
})

test('続きを足すときは、同じ ID のものを重ねず、順番を保つ', () => {
  const current = [{ id: 1 }, { id: 2 }, { id: 9 }]

  expect(mergeById(current, [{ id: 3 }, { id: 9 }, { id: 4 }])).toEqual([
    { id: 1 },
    { id: 2 },
    { id: 9 },
    { id: 3 },
    { id: 4 },
  ])
  expect(mergeById(current, [])).toBe(current)
})

describe('返信の前に出す返信先', () => {
  const reply = (body: string, replyTo: string | null) => ({
    id: 1,
    author: { username: 'yamada', displayName: '山田太郎', iconUrl: null, isPrivate: false },
    replyToUser:
      replyTo === null
        ? null
        : { username: replyTo, displayName: '佐藤花子', iconUrl: null, isPrivate: false },
    body,
    createdAt: '2026-11-25T10:00:00+09:00',
    deletable: false,
  })

  test('返信先の @ユーザー名 を出す', () => {
    expect(mentionToShow(reply('ありがとうございます！', 'sato'))).toBe('sato')
  })

  test('本文がすでに同じ @ユーザー名 で始まっていれば重ねない（返信への返信の入力欄に入れておいたもの）', () => {
    expect(mentionToShow(reply('@sato ありがとう', 'sato'))).toBeNull()
    // 名前の一部だけが同じなら、別の人
    expect(mentionToShow(reply('@sato2 こんにちは', 'sato'))).toBe('sato')
  })

  test('返信先が退会していれば出さない', () => {
    expect(mentionToShow(reply('ありがとう', null))).toBeNull()
  })
})
