import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { BackButton } from '../components/BackButton'
import { FollowButton } from '../components/FollowButton'
import { LoadMore } from '../components/LoadMore'
import { LockMark } from '../components/LockMark'
import { WorkoutCard } from '../components/WorkoutCard'
import { ApiError, errorMessage, fetchProfile, fetchUserWorkouts } from '../lib/api/client'
import type { Profile } from '../lib/api/types'
import styles from './ProfilePage.module.css'

/**
 * S-40 プロフィール（docs/screens.md）。鍵アカウントでも、アイコン・表示名・自己紹介・数は出す（F-50）。
 *
 * 記録は、見る権限があるときだけ読み込む（実施日の新しい順に20件ずつ）。公開している目標は段階 3 で足す
 */
export function ProfilePage() {
  const { username = '' } = useParams()
  const query = useQuery({
    queryKey: ['users', username, 'profile'],
    queryFn: () => fetchProfile(username),
  })

  if (query.error) {
    const notFound = query.error instanceof ApiError && query.error.code === 'USER_NOT_FOUND'
    return (
      <>
        <BackButton fallback="/">プロフィール</BackButton>
        <div className={styles.message}>
          {notFound ? (
            <p>このアカウントは存在しません</p>
          ) : (
            <>
              <p>{errorMessage(query.error)}</p>
              <button
                type="button"
                className="button-secondary"
                onClick={() => void query.refetch()}
              >
                もう一度読み込む
              </button>
            </>
          )}
        </div>
      </>
    )
  }
  if (!query.data) {
    return <p className={styles.message}>読み込み中…</p>
  }
  return <ProfileView profile={query.data} />
}

function ProfileView({ profile }: { profile: Profile }) {
  const base = `/users/${encodeURIComponent(profile.username)}`
  return (
    <>
      <BackButton fallback="/">{profile.displayName}</BackButton>
      <section className={styles.card}>
        <div className={styles.top}>
          <Avatar displayName={profile.displayName} iconUrl={profile.iconUrl} size={80} />
          {profile.me ? (
            <Link to="/settings/profile" className={`button-secondary ${styles.editLink}`}>
              プロフィールを編集
            </Link>
          ) : (
            <FollowButton
              username={profile.username}
              status={profile.followStatus}
              isPrivate={profile.isPrivate}
            />
          )}
        </div>
        <h1 className={styles.name}>
          {profile.displayName}
          {profile.isPrivate && <LockMark />}
        </h1>
        <p className={styles.username}>@{profile.username}</p>
        {profile.bio && <p className={styles.bio}>{profile.bio}</p>}
        <p className={styles.counts}>
          <Count
            to={profile.canViewRecords ? `${base}/following` : null}
            label={`${profile.followingCount} フォロー中`}
          />
          <Count
            to={profile.canViewRecords ? `${base}/followers` : null}
            label={`${profile.followerCount} フォロワー`}
          />
        </p>
      </section>
      {profile.canViewRecords ? (
        <UserWorkouts username={profile.username} />
      ) : (
        <p className={styles.locked}>
          <span aria-hidden="true">🔒 </span>
          このアカウントの記録は、承認されたフォロワーだけが見られます
        </p>
      )}
    </>
  )
}

/** フォロー中・フォロワーの数。見る権限がなければ押せない（ただの文字） */
function Count({ to, label }: { to: string | null; label: string }) {
  return to ? (
    <Link to={to} className={styles.count}>
      {label}
    </Link>
  ) : (
    <span className={styles.count}>{label}</span>
  )
}

/** その人の記録（実施日の新しい順に20件ずつ。下までスクロールで続きを読み込む。F-62） */
function UserWorkouts({ username }: { username: string }) {
  const query = useInfiniteQuery({
    queryKey: ['users', username, 'workouts'],
    queryFn: ({ pageParam }) => fetchUserWorkouts(username, pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
  const items = query.data?.pages.flatMap((page) => page.items) ?? []
  // 「◯分前に投稿」の基準。描き直すたびに変わらないよう、開いたときの時刻を持つ
  const [now] = useState(() => new Date())

  return (
    <section aria-labelledby="profile-workouts" className={styles.workouts}>
      <h2 id="profile-workouts" className={styles.sectionTitle}>
        記録
      </h2>
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
        <p className={styles.message}>まだ記録がありません</p>
      ) : (
        <>
          <ul className={styles.workoutList}>
            {items.map((item) => (
              <li key={item.id}>
                <WorkoutCard workout={item} now={now} />
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
    </section>
  )
}
