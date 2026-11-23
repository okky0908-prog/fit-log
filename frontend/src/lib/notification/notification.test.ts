import { describe, expect, test } from 'vitest'
import type { NotificationItem } from '../api/types'
import {
  formatNotificationTime,
  formatUnreadCount,
  notificationLink,
  notificationMessage,
} from './notification'

// 通知の文言・移動先・時刻（docs/features.md「通知」、docs/screens.md S-30・ナビゲーション）

const SATO = { username: 'sato', displayName: '佐藤花子', iconUrl: null, isPrivate: false }

function item(values: Partial<NotificationItem>): NotificationItem {
  return {
    id: 1,
    type: 'follow',
    createdAt: '2026-11-23T10:00:00+09:00',
    read: false,
    actor: SATO,
    workout: null,
    comment: null,
    goal: null,
    ...values,
  }
}

describe('文言', () => {
  test.each([
    ['like', '佐藤花子さんがあなたの記録にナイストレーニングしました'],
    ['comment', '佐藤花子さんがあなたの記録にコメントしました'],
    ['reply', '佐藤花子さんがあなたのコメントに返信しました'],
    ['follow', '佐藤花子さんがあなたをフォローしました'],
    ['follow_request', '佐藤花子さんからフォローのリクエストが届きました'],
    ['follow_accepted', '佐藤花子さんがフォローのリクエストを承認しました'],
    ['goal_cheer', '佐藤花子さんがあなたの目標を応援しています'],
  ] as const)('%s', (type, message) => {
    expect(notificationMessage(item({ type }))).toBe(message)
  })

  test('目標の達成は、操作した人がいない', () => {
    expect(notificationMessage(item({ type: 'goal_achieved', actor: null }))).toBe(
      '目標を達成しました',
    )
  })

  test('知らない種類（API に種類が増えたとき）は、決まった文言にする', () => {
    expect(notificationMessage(item({ type: 'unknown' as NotificationItem['type'] }))).toBe(
      '新しいお知らせがあります',
    )
  })
})

describe('移動先', () => {
  test('投稿についての通知は投稿詳細、コメント・返信はそのコメントの位置', () => {
    const workout = { id: 12, bodyExcerpt: '脚の日' }
    expect(notificationLink(item({ type: 'like', workout }))).toBe('/workouts/12')
    expect(
      notificationLink(item({ type: 'comment', workout, comment: { id: 5, bodyExcerpt: '' } })),
    ).toBe('/workouts/12#comment-5')
    expect(
      notificationLink(item({ type: 'reply', workout, comment: { id: 6, bodyExcerpt: '' } })),
    ).toBe('/workouts/12#comment-6')
  })

  test('フォロー・承認はその人のプロフィール、リクエストはリクエスト一覧、目標は目標一覧', () => {
    expect(notificationLink(item({ type: 'follow' }))).toBe('/users/sato')
    expect(notificationLink(item({ type: 'follow_accepted' }))).toBe('/users/sato')
    expect(notificationLink(item({ type: 'follow_request' }))).toBe('/follow-requests')
    expect(notificationLink(item({ type: 'goal_achieved', actor: null }))).toBe('/goals')
    expect(notificationLink(item({ type: 'goal_cheer' }))).toBe('/goals')
  })

  test('対象がない・知らない種類は、移動先なし', () => {
    expect(notificationLink(item({ type: 'like', workout: null }))).toBeNull()
    expect(notificationLink(item({ type: 'follow', actor: null }))).toBeNull()
    expect(notificationLink(item({ type: 'unknown' as NotificationItem['type'] }))).toBeNull()
  })
})

describe('時刻', () => {
  const now = new Date('2026-11-23T10:00:00+09:00')

  test('1時間以内は分、24時間以内は時間（1分未満も「1分前」）', () => {
    expect(formatNotificationTime('2026-11-23T09:59:30+09:00', now)).toBe('1分前')
    expect(formatNotificationTime('2026-11-23T09:01:00+09:00', now)).toBe('59分前')
    expect(formatNotificationTime('2026-11-23T09:00:00+09:00', now)).toBe('1時間前')
    expect(formatNotificationTime('2026-11-22T10:00:01+09:00', now)).toBe('23時間前')
  })

  test('それより前は、昨日（日本時間）なら「昨日」、今年なら月日、去年以前は年も', () => {
    expect(formatNotificationTime('2026-11-22T10:00:00+09:00', now)).toBe('昨日')
    expect(formatNotificationTime('2026-11-22T00:00:00+09:00', now)).toBe('昨日')
    expect(formatNotificationTime('2026-11-21T23:59:59+09:00', now)).toBe('11/21')
    expect(formatNotificationTime('2025-12-31T12:00:00+09:00', now)).toBe('2025/12/31')
  })
})

describe('未読の数', () => {
  test('0 なら出さず、100 以上は「99+」', () => {
    expect(formatUnreadCount(0)).toBeNull()
    expect(formatUnreadCount(1)).toBe('1')
    expect(formatUnreadCount(99)).toBe('99')
    expect(formatUnreadCount(100)).toBe('99+')
  })
})
