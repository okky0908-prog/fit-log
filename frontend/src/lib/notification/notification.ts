import type { NotificationItem } from '../api/types'
import { tokyoDate } from '../format/date'

/*
 * 通知の文言・移動先・時刻（docs/features.md「通知」、docs/screens.md S-30）。
 * API は種類（type）だけを返し、文言は画面が作る（docs/api.md「Notification」）
 */

/** 未読の数を数えるのをやめる数（A-121）。これ以上は「99+」 */
const UNREAD_DISPLAY_MAX = 99

/** 種類ごとの文言。〇〇は操作した人の表示名 */
const MESSAGES: Record<string, (name: string) => string> = {
  like: (name) => `${name}さんがあなたの記録にナイストレーニングしました`,
  comment: (name) => `${name}さんがあなたの記録にコメントしました`,
  reply: (name) => `${name}さんがあなたのコメントに返信しました`,
  follow: (name) => `${name}さんがあなたをフォローしました`,
  follow_request: (name) => `${name}さんからフォローのリクエストが届きました`,
  follow_accepted: (name) => `${name}さんがフォローのリクエストを承認しました`,
  // 目標の名前は、目標を作る段階 3 で足す（「目標「ベンチプレス 100kg」を達成しました」）
  goal_achieved: () => '目標を達成しました',
  goal_cheer: (name) => `${name}さんがあなたの目標を応援しています`,
}

export function notificationMessage(item: NotificationItem): string {
  const message = MESSAGES[item.type]
  return message ? message(item.actor?.displayName ?? '') : '新しいお知らせがあります'
}

/**
 * 押したときの移動先（docs/screens.md S-30）。投稿→投稿詳細、コメント・返信→投稿詳細のそのコメント、フォロー・承認→その人のプロフィール、
 * リクエスト→リクエスト一覧、目標の達成・応援→目標一覧。対象がなければ null（押せない）
 */
export function notificationLink(item: NotificationItem): string | null {
  switch (item.type) {
    case 'like':
      return item.workout ? `/workouts/${item.workout.id}` : null
    case 'comment':
    case 'reply':
      return item.workout && item.comment
        ? `/workouts/${item.workout.id}#comment-${item.comment.id}`
        : null
    case 'follow':
    case 'follow_accepted':
      return item.actor ? `/users/${item.actor.username}` : null
    case 'follow_request':
      return '/follow-requests'
    case 'goal_achieved':
    case 'goal_cheer':
      return '/goals'
    default:
      return null
  }
}

/**
 * 通知の時刻。1時間以内は「3分前」（1分未満も「1分前」）、24時間以内は「5時間前」、それより前は、昨日（日本時間）なら「昨日」、
 * 今年なら「11/21」、去年以前は「2025/12/31」
 */
export function formatNotificationTime(createdAt: string, now: Date): string {
  const created = new Date(createdAt)
  const minutes = Math.floor((now.getTime() - created.getTime()) / 60_000)
  if (minutes < 60) {
    return `${Math.max(minutes, 1)}分前`
  }
  if (minutes < 24 * 60) {
    return `${Math.floor(minutes / 60)}時間前`
  }
  const date = tokyoDate(created)
  if (date === tokyoDate(new Date(now.getTime() - 24 * 60 * 60 * 1000))) {
    return '昨日'
  }
  const [year, month, day] = date.split('-').map(Number)
  const monthDay = `${month}/${day}`
  return String(year) === tokyoDate(now).slice(0, 4) ? monthDay : `${year}/${monthDay}`
}

/** ナビゲーションの 🔔 の数。0 なら出さない、100 以上は「99+」 */
export function formatUnreadCount(count: number): string | null {
  if (count <= 0) {
    return null
  }
  return count > UNREAD_DISPLAY_MAX ? `${UNREAD_DISPLAY_MAX}+` : String(count)
}
