import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { Avatar } from '../components/Avatar'
import { BackButton } from '../components/BackButton'
import { FollowButton } from '../components/FollowButton'
import { LockMark } from '../components/LockMark'
import { ApiError, errorMessage, fetchProfile } from '../lib/api/client'
import type { Profile } from '../lib/api/types'
import styles from './ProfilePage.module.css'

/**
 * S-40 プロフィール（docs/screens.md）。鍵アカウントでも、アイコン・表示名・自己紹介・数は出す（F-50）。
 *
 * 公開している目標（段階 3）と記録の一覧（docs/test-plan.md 8.2 の 12）は、それぞれを作るときに足す
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
      {!profile.canViewRecords && (
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
