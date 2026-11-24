import type { ReactNode } from 'react'
import type { UserSummary, Visibility } from '../lib/api/types'
import { formatPerformedOn, formatPostedAgo } from '../lib/format/date'
import { Avatar } from './Avatar'
import { VisibilityIcon } from './EmojiIcon'
import { LockMark } from './LockMark'
import { UserLink } from './UserLink'
import { WorkoutOwnerMenu } from './WorkoutOwnerMenu'
import styles from './WorkoutHeader.module.css'

interface Props {
  workout: {
    id: number
    author: UserSummary
    performedOn: string
    createdAt: string
    editedAt: string | null
    visibility: Visibility
    mine: boolean
  }
  /** 「◯分前に投稿」の基準の時刻 */
  now: Date
  /** 自分の記録を削除したあと */
  onDeleted: () => void
  /** 実施日を包む（投稿カードでは、実施日を詳細へのリンクにする） */
  renderPerformedOn?: (label: string) => ReactNode
}

/**
 * 記録の頭の部分（投稿カード・投稿詳細で共通。docs/screens.md「投稿カード」）：投稿者のアイコン・表示名・@ユーザー名、
 * 実施日・投稿日時・編集済み・公開範囲、自分の記録なら `…` メニュー
 */
export function WorkoutHeader({ workout, now, onDeleted, renderPerformedOn }: Props) {
  const postedAgo = formatPostedAgo(workout.createdAt, workout.performedOn, now)
  const performedOn = formatPerformedOn(workout.performedOn, now)
  return (
    <header className={styles.head}>
      <div className={styles.byline}>
        <UserLink username={workout.author.username} plain>
          <Avatar
            displayName={workout.author.displayName}
            iconUrl={workout.author.iconUrl}
            size={40}
          />
          {/* アイコンは読み上げないので、リンクの名前を補う */}
          <span className="visually-hidden">{workout.author.displayName}のプロフィール</span>
        </UserLink>
        <div>
          <p className={styles.author}>
            <UserLink username={workout.author.username} className={styles.displayName}>
              {workout.author.displayName}
            </UserLink>
            {workout.author.isPrivate && <LockMark />}
            <UserLink username={workout.author.username} className={styles.username}>
              @{workout.author.username}
            </UserLink>
          </p>
          <p className={styles.meta}>
            <span>{renderPerformedOn ? renderPerformedOn(performedOn) : performedOn}</span>
            {postedAgo && <span>{postedAgo}</span>}
            {workout.editedAt && <span>編集済み</span>}
            <VisibilityIcon visibility={workout.visibility} />
          </p>
        </div>
      </div>
      {workout.mine && <WorkoutOwnerMenu workoutId={workout.id} onDeleted={onDeleted} />}
    </header>
  )
}
