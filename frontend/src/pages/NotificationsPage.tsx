import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { errorMessage, fetchNotifications, markNotificationsRead } from '../lib/api/client'
import type { NotificationItem } from '../lib/api/types'
import {
  formatNotificationTime,
  notificationLink,
  notificationMessage,
} from '../lib/notification/notification'
import styles from './NotificationsPage.module.css'

/**
 * S-30 通知一覧（docs/screens.md）。新しい順に20件ずつ、下までスクロールで続きを読み込む（「さらに表示」でも読み込める）。
 * 開いたら、表示した未読の通知を既読にする。未読の ● は画面を離れるまで残す（どれが新しかったか分かるように）。
 *
 * フォローのリクエストの通知の「承認」「拒否」は、フォローを作るとき（docs/test-plan.md 8.2 の 11）に足す
 */
export function NotificationsPage() {
  const query = useInfiniteQuery({
    queryKey: ['notifications', 'list'],
    queryFn: ({ pageParam }) => fetchNotifications(pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
  const items = query.data?.pages.flatMap((page) => page.items) ?? []
  useMarkRead(items)
  // 「◯分前」の基準。描き直すたびに変わらないよう、開いたときの時刻を持つ
  const [now] = useState(() => new Date())

  return (
    <>
      <h1 className={styles.title}>通知</h1>
      {query.error ? (
        <div className={styles.message}>
          <p>{errorMessage(query.error)}</p>
          <button type="button" className="button-secondary" onClick={() => void query.refetch()}>
            もう一度読み込む
          </button>
        </div>
      ) : !query.data ? (
        <p className={styles.message}>読み込み中…</p>
      ) : items.length === 0 ? (
        <p className={styles.message}>通知はまだありません</p>
      ) : (
        <>
          <ul className={styles.list}>
            {items.map((item) => (
              <li key={item.id} className={item.read ? styles.item : styles.unread}>
                <NotificationRow item={item} now={now} />
              </li>
            ))}
          </ul>
          {query.hasNextPage && (
            <LoadMore
              loading={query.isFetchingNextPage}
              onLoad={() => void query.fetchNextPage()}
            />
          )}
        </>
      )}
    </>
  )
}

/**
 * 表示した未読の通知を既読にし、ナビゲーションの未読の数を取り直す。同じ通知を2回送らない。
 * 既読にできなくても、一覧はそのまま使える（次に開いたときにもう一度送る）
 */
function useMarkRead(items: NotificationItem[]) {
  const sent = useRef(new Set<number>())
  const queryClient = useQueryClient()
  useEffect(() => {
    const ids = items.filter((item) => !item.read && !sent.current.has(item.id)).map((i) => i.id)
    if (ids.length === 0) return
    ids.forEach((id) => sent.current.add(id))
    markNotificationsRead(ids)
      .then(() => queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] }))
      .catch(() => {
        // 既読にできなかった。● はそのまま
      })
  }, [items, queryClient])
}

function NotificationRow({ item, now }: { item: NotificationItem; now: Date }) {
  const link = notificationLink(item)
  const excerpt = item.comment?.bodyExcerpt ?? item.workout?.bodyExcerpt
  const content = (
    <>
      {!item.read && (
        <span className={styles.dot}>
          <span aria-hidden="true">●</span>
          <span className="visually-hidden">未読</span>
        </span>
      )}
      <span className={styles.avatar} aria-hidden="true">
        {item.actor ? item.actor.displayName.slice(0, 1) : '🎯'}
      </span>
      <span className={styles.body}>
        <span className={styles.text}>
          {notificationMessage(item)}
          {excerpt ? `「${excerpt}」` : ''}
        </span>
        <span className={styles.time}>· {formatNotificationTime(item.createdAt, now)}</span>
      </span>
    </>
  )
  return link ? (
    <Link to={link} className={styles.row}>
      {content}
    </Link>
  ) : (
    <div className={styles.row}>{content}</div>
  )
}

/** 続きの読み込み。下までスクロールしたら読み込み、ボタンでも読み込める（キーボード・読み上げで使えるように） */
function LoadMore({ loading, onLoad }: { loading: boolean; onLoad: () => void }) {
  const ref = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const target = ref.current
    if (!target || typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting) && !loading) onLoad()
    })
    observer.observe(target)
    return () => observer.disconnect()
  }, [loading, onLoad])
  return (
    <button
      ref={ref}
      type="button"
      className={`button-secondary ${styles.more}`}
      disabled={loading}
      onClick={onLoad}
    >
      {loading ? '読み込み中…' : 'さらに表示'}
    </button>
  )
}
