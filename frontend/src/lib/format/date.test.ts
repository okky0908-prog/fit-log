import { describe, expect, test } from 'vitest'
import { formatPerformedOn, formatPostedAgo } from './date'

// 投稿カード・投稿詳細の日付（docs/screens.md「投稿カード」）。日付の区切りは日本時間

const at = (iso: string) => new Date(iso)

describe('実施日', () => {
  test('今年なら年を省き、曜日を付ける', () => {
    expect(formatPerformedOn('2026-11-14', at('2026-11-22T10:00:00+09:00'))).toBe('11/14（土）')
    expect(formatPerformedOn('2026-01-05', at('2026-11-22T10:00:00+09:00'))).toBe('1/5（月）')
  })

  test('今年でなければ年を付ける', () => {
    expect(formatPerformedOn('2025-12-31', at('2026-11-22T10:00:00+09:00'))).toBe(
      '2025/12/31（水）',
    )
  })

  test('今年かどうかは日本時間で決める', () => {
    // 日本時間 2027-01-01 00:00（UTC では 2026-12-31 15:00）。もう2027年なので、2026年の日付には年を付ける
    expect(formatPerformedOn('2026-12-31', at('2027-01-01T00:00:00+09:00'))).toBe(
      '2026/12/31（木）',
    )
    // 日本時間 2026-12-31 23:59（UTC でも 2026年）
    expect(formatPerformedOn('2026-12-31', at('2026-12-31T23:59:59+09:00'))).toBe('12/31（木）')
  })
})

describe('投稿日時', () => {
  const performedOn = '2026-11-20'

  test('1時間以内は分、24時間以内は時間で出す', () => {
    const now = at('2026-11-22T10:00:00+09:00')
    expect(formatPostedAgo('2026-11-22T09:59:30+09:00', performedOn, now)).toBe('1分前に投稿')
    expect(formatPostedAgo('2026-11-22T09:57:00+09:00', performedOn, now)).toBe('3分前に投稿')
    expect(formatPostedAgo('2026-11-22T09:00:01+09:00', performedOn, now)).toBe('59分前に投稿')
    expect(formatPostedAgo('2026-11-22T09:00:00+09:00', performedOn, now)).toBe('1時間前に投稿')
    expect(formatPostedAgo('2026-11-21T10:00:01+09:00', performedOn, now)).toBe('23時間前に投稿')
  })

  test('24時間より前は出さない', () => {
    const now = at('2026-11-22T10:00:00+09:00')
    expect(formatPostedAgo('2026-11-21T10:00:00+09:00', performedOn, now)).toBeNull()
  })

  test('実施日と同じ日（日本時間）に投稿したときは出さない', () => {
    const now = at('2026-11-20T21:00:00+09:00')
    expect(formatPostedAgo('2026-11-20T20:00:00+09:00', performedOn, now)).toBeNull()
    // UTC では前の日（2026-11-19 15:30）だが、日本時間では実施日と同じ日
    expect(formatPostedAgo('2026-11-20T00:30:00+09:00', performedOn, now)).toBeNull()
  })
})
