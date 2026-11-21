/*
 * 投稿カード・投稿詳細の日付（docs/screens.md「投稿カード」）。
 * 日付の区切りは日本時間で決める（docs/api.md「共通のルール」）。端末のタイムゾーンには頼らない
 */

const WEEKDAYS = ['日', '月', '火', '水', '木', '金', '土']

const TOKYO_DATE = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** 日本時間の日付（YYYY-MM-DD） */
export function tokyoDate(instant: Date): string {
  return TOKYO_DATE.format(instant)
}

/** 実施日。「11/14（土）」の形で、今年（日本時間）でなければ「2025/12/31（水）」と年を付ける */
export function formatPerformedOn(performedOn: string, now: Date): string {
  const [year, month, day] = performedOn.split('-').map(Number)
  const weekday = WEEKDAYS[new Date(Date.UTC(year, month - 1, day)).getUTCDay()]
  const monthDay = `${month}/${day}（${weekday}）`
  return String(year) === tokyoDate(now).slice(0, 4) ? monthDay : `${year}/${monthDay}`
}

/**
 * 投稿日時。1時間以内は「3分前に投稿」（1分未満も「1分前」）、24時間以内は「5時間前に投稿」。
 * それより前と、実施日と同じ日（日本時間）に投稿したときは出さない（null）
 */
export function formatPostedAgo(createdAt: string, performedOn: string, now: Date): string | null {
  const posted = new Date(createdAt)
  if (tokyoDate(posted) === performedOn) {
    return null
  }
  const minutes = Math.floor((now.getTime() - posted.getTime()) / 60_000)
  if (minutes < 60) {
    return `${Math.max(minutes, 1)}分前に投稿`
  }
  if (minutes < 24 * 60) {
    return `${Math.floor(minutes / 60)}時間前に投稿`
  }
  return null
}
