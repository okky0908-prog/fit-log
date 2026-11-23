import type { UserListItem } from '../lib/api/types'
import { Avatar } from './Avatar'
import { FollowButton } from './FollowButton'
import { LockMark } from './LockMark'
import { UserLink } from './UserLink'
import styles from './UserListRow.module.css'

/**
 * ユーザー一覧の1行（docs/screens.md「ユーザー一覧の行（共通）」。S-42・S-43・S-44 で共通）。
 * アイコン・表示名・@ユーザー名・自己紹介と、フォローボタン（自分の行には出さない）
 */
export function UserListRow({ user }: { user: UserListItem }) {
  return (
    <li className={styles.row}>
      <UserLink username={user.username} plain>
        <Avatar displayName={user.displayName} iconUrl={user.iconUrl} size={44} />
        {/* アイコンは読み上げないので、リンクの名前を補う */}
        <span className="visually-hidden">{user.displayName}のプロフィール</span>
      </UserLink>
      <div className={styles.body}>
        <p className={styles.names}>
          <UserLink username={user.username} className={styles.displayName}>
            {user.displayName}
          </UserLink>
          {user.isPrivate && <LockMark />}
          <UserLink username={user.username} className={styles.username}>
            @{user.username}
          </UserLink>
        </p>
        {user.bio && <p className={styles.bio}>{user.bio}</p>}
      </div>
      {!user.me && (
        <FollowButton
          username={user.username}
          status={user.followStatus}
          isPrivate={user.isPrivate}
        />
      )}
    </li>
  )
}
