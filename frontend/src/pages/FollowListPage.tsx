import { useInfiniteQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { BackButton } from '../components/BackButton'
import { LoadMore } from '../components/LoadMore'
import { UserListRow } from '../components/UserListRow'
import { ApiError, errorMessage, fetchFollowers, fetchFollowing } from '../lib/api/client'
import styles from './FollowListPage.module.css'

type Kind = 'following' | 'followers'

const TABS: { kind: Kind; label: string; empty: string }[] = [
  { kind: 'following', label: 'フォロー中', empty: 'まだ誰もフォローしていません' },
  { kind: 'followers', label: 'フォロワー', empty: 'まだフォロワーはいません' },
]

/**
 * S-42 フォロー中・フォロワー一覧（docs/screens.md）。承認済みの相手だけを、フォローした新しい順に20件ずつ出す。
 * タブの切り替えでは履歴を増やさない（「←」・ブラウザの「戻る」でプロフィールに戻れるように）
 */
export function FollowListPage({ kind }: { kind: Kind }) {
  const { username = '' } = useParams()
  const base = `/users/${encodeURIComponent(username)}`
  const query = useInfiniteQuery({
    queryKey: ['users', username, kind],
    queryFn: ({ pageParam }) =>
      (kind === 'following' ? fetchFollowing : fetchFollowers)(username, pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
  const items = query.data?.pages.flatMap((page) => page.items) ?? []
  const tab = TABS.find((t) => t.kind === kind) ?? TABS[0]

  return (
    <>
      <BackButton fallback={base}>{`@${username}`}</BackButton>
      <div role="tablist" className={styles.tabs}>
        {TABS.map((t) => (
          <Link
            key={t.kind}
            to={`${base}/${t.kind}`}
            replace
            role="tab"
            aria-selected={t.kind === kind}
            className={styles.tab}
          >
            {t.label}
          </Link>
        ))}
      </div>
      <div role="tabpanel" aria-label={tab.label}>
        {query.error ? (
          <ErrorMessage error={query.error} onRetry={() => void query.refetch()} />
        ) : !query.data ? (
          <p className={styles.message}>読み込み中…</p>
        ) : items.length === 0 ? (
          <p className={styles.message}>{tab.empty}</p>
        ) : (
          <>
            <ul className={styles.list}>
              {items.map((item) => (
                <UserListRow key={item.username} user={item} />
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
      </div>
    </>
  )
}

function ErrorMessage({ error, onRetry }: { error: Error; onRetry: () => void }) {
  const code = error instanceof ApiError ? error.code : null
  if (code === 'PRIVATE_ACCOUNT') {
    return (
      <p className={styles.message}>
        <span aria-hidden="true">🔒 </span>
        このアカウントのフォロー・フォロワーは、承認されたフォロワーだけが見られます
      </p>
    )
  }
  if (code === 'USER_NOT_FOUND') {
    return <p className={styles.message}>このアカウントは存在しません</p>
  }
  return (
    <div className={styles.message}>
      <p>{errorMessage(error)}</p>
      <button type="button" className="button-secondary" onClick={onRetry}>
        もう一度読み込む
      </button>
    </div>
  )
}
