import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { LoadMore } from '../components/LoadMore'
import { WorkoutCard } from '../components/WorkoutCard'
import {
  errorMessage,
  fetchNewPostCount,
  fetchTimeline,
  type TimelineKind,
} from '../lib/api/client'
import { newPostsLabel } from '../lib/timeline/timeline'
import { useRefreshTimeline } from '../lib/timeline/useRefreshTimeline'
import styles from './HomePage.module.css'

/** 新しい投稿の件数を確かめる間隔（F-24）。ブラウザのタブが裏にある間は確かめない（TanStack Query の既定） */
const NEW_COUNT_INTERVAL_MS = 60_000

const TABS: { kind: TimelineKind; label: string; path: string }[] = [
  { kind: 'following', label: 'フォロー中', path: '/' },
  { kind: 'all', label: '全体', path: '/all' },
]

/**
 * S-10 ホーム（タイムライン。docs/screens.md）。フォロー中（`/`）と全体（`/all`）を、投稿日時の新しい順に20件ずつ出し、
 * 下までスクロールで続きを読み込む。60秒ごとに新しい投稿の件数だけを確かめ、あれば「↑ N件の新しい投稿」を出す（一覧は勝手に増やさない）。
 *
 * 今週のひとこと（自分の今週のトレーニング日数と目標の達成率）は、集計を作る段階 2 で足す
 */
export function HomePage({ kind }: { kind: TimelineKind }) {
  const refresh = useRefreshTimeline()
  const query = useInfiniteQuery({
    queryKey: ['timeline', kind],
    queryFn: ({ pageParam }) => fetchTimeline(kind, pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
  const items = query.data?.pages.flatMap((page) => page.items) ?? []
  // 「◯分前に投稿」の基準。描き直すたびに変わらないよう、開いたときの時刻を持つ
  const [now] = useState(() => new Date())

  return (
    <>
      <h1 className="visually-hidden">ホーム</h1>
      <div role="tablist" className={styles.tabs}>
        {TABS.map((tab) => (
          <Link
            key={tab.kind}
            to={tab.path}
            replace
            role="tab"
            aria-selected={tab.kind === kind}
            className={styles.tab}
            // 表示中のタブをもう一度押したら、最新を取り直して一番上へ
            onClick={() => tab.kind === kind && refresh()}
          >
            {tab.label}
          </Link>
        ))}
      </div>
      <div role="tabpanel" aria-label={TABS.find((tab) => tab.kind === kind)?.label}>
        {query.error ? (
          <div className={styles.message}>
            <p>{errorMessage(query.error)}</p>
            <button type="button" className="button-secondary" onClick={() => void query.refetch()}>
              もう一度読み込む
            </button>
          </div>
        ) : !query.data ? (
          <p className={styles.message}>読み込み中…</p>
        ) : (
          <>
            <NewPostsButton kind={kind} since={items[0]?.id ?? 0} onShow={refresh} />
            {items.length === 0 ? (
              <EmptyTimeline kind={kind} />
            ) : (
              <>
                <ul className={styles.list}>
                  {items.map((item) => (
                    <li key={item.id}>
                      <WorkoutCard workout={item} now={now} />
                    </li>
                  ))}
                </ul>
                {query.hasNextPage ? (
                  <LoadMore
                    loading={query.isFetchingNextPage}
                    onLoad={() => void query.fetchNextPage()}
                  />
                ) : (
                  <p className={styles.message}>これ以上の投稿はありません</p>
                )}
              </>
            )}
          </>
        )}
      </div>
    </>
  )
}

/** 「↑ N件の新しい投稿」。0件なら出さない。確かめられなかったときも出さない */
function NewPostsButton({
  kind,
  since,
  onShow,
}: {
  kind: TimelineKind
  since: number
  onShow: () => void
}) {
  const count = useQuery({
    queryKey: ['timeline', kind, 'new-count', since],
    queryFn: () => fetchNewPostCount(kind, since),
    refetchInterval: NEW_COUNT_INTERVAL_MS,
  })
  const label = newPostsLabel(count.data?.count ?? 0)
  if (!label) return null
  return (
    <button type="button" className={styles.newPosts} onClick={onShow}>
      {label}
    </button>
  )
}

function EmptyTimeline({ kind }: { kind: TimelineKind }) {
  if (kind === 'all') {
    return <p className={styles.message}>まだ誰も記録していません</p>
  }
  return (
    <div className={styles.message}>
      <p>まだ記録がありません。最初の記録をつけてみましょう</p>
      <Link to="/workouts/new" className="button-primary">
        ＋ 記録する
      </Link>
      {/* ユーザー検索（S-44）は段階 4 で作るので、今は案内の文だけ */}
      <p>ユーザーを検索して、仲間をフォローしてみましょう</p>
    </div>
  )
}
