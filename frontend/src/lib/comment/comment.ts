import type { Reply } from '../api/types'

/*
 * S-12 のコメント欄の決まり（docs/screens.md S-12、F-30〜F-33）
 */

/** コメントの本文の文字数の上限（F-30） */
export const COMMENT_MAX_LENGTH = 140

/** 本文の文字数。前後の空白・改行を除いて数え、絵文字も1文字（サーバーの数え方と同じ） */
export function commentLength(text: string): number {
  return [...text.trim()].length
}

/** 投稿できるか（1〜140文字） */
export function canPostComment(text: string): boolean {
  const length = commentLength(text)
  return length >= 1 && length <= COMMENT_MAX_LENGTH
}

/** 返信への返信の入力欄に、最初から入れておく文字（F-33） */
export function replyPrefix(username: string): string {
  return `@${username} `
}

/** 「返信をさらに表示（N件）」の N。まだ表示していない返信の数（0 を下回らない） */
export function remainingReplies(replyCount: number, shown: number): number {
  return Math.max(0, replyCount - shown)
}

/**
 * 表示している一覧に、続き・投稿したものを足す。同じ ID のものは重ねない（投稿した返信のあとで続きを読み込んだときなど。
 * docs/api.md A-65）。足すものがなければ同じ一覧を返す
 */
export function mergeById<T extends { id: number }>(current: T[], added: T[]): T[] {
  const ids = new Set(current.map((item) => item.id))
  const fresh = added.filter((item) => !ids.has(item.id))
  return fresh.length === 0 ? current : [...current, ...fresh]
}

/** 返信の前に出す返信先（@ユーザー名）。返信先が退会していれば出さない。本文がすでに同じ @ユーザー名 で始まっていれば重ねない（null） */
export function mentionToShow(reply: Reply): string | null {
  if (reply.replyToUser === null) return null
  const { username } = reply.replyToUser
  // 「@sato2」は「@sato」で始まるが別の人なので、名前のあとが空白か本文の終わりかまで見る
  const alreadyMentioned = new RegExp(`^@${username}(\\s|$)`).test(reply.body)
  return alreadyMentioned ? null : username
}
