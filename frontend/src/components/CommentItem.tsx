import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { deleteComment, errorMessage, fetchReplies, postComment } from '../lib/api/client'
import type { Comment, Reply, UserSummary } from '../lib/api/types'
import { mentionToShow, mergeById, remainingReplies, replyPrefix } from '../lib/comment/comment'
import { formatNotificationTime } from '../lib/notification/notification'
import { useToast } from '../lib/toast'
import { Avatar } from './Avatar'
import { CommentForm } from './CommentForm'
import styles from './CommentItem.module.css'
import { ConfirmDialog } from './ConfirmDialog'
import { UserLink } from './UserLink'

interface Props {
  workoutId: number
  comment: Comment
  /** 削除したコメント・返信の ID（一覧から外す） */
  deleted: ReadonlySet<number>
  onDeleted: (id: number, commentCount: number) => void
  /** 返信を投稿したあと。投稿後のコメント数を渡す */
  onReplied: (commentCount: number) => void
}

/**
 * コメント1件と、その返信（docs/screens.md S-12、F-31〜F-33）。
 *
 * - 返信は古い順に3件まで出し、4件以上あれば「返信をさらに表示（N件）」で20件ずつ読み込む。全部出したら消す
 * - 「返信」で、この返信元の下に入力欄を出す。返信への返信は、入力欄の先頭に @ユーザー名 を入れておく
 * - 投稿した返信は、表示している返信の最後にすぐ足す。そのあとで続きを読み込んでも、同じ返信を重ねて出さない
 */
export function CommentItem({ workoutId, comment, deleted, onDeleted, onReplied }: Props) {
  const toast = useToast()
  // 続きで読み込んだ返信と、投稿した返信
  const [loaded, setLoaded] = useState<Reply[]>([])
  const [posted, setPosted] = useState<Reply[]>([])
  const [cursor, setCursor] = useState(comment.repliesNextCursor)
  // 返信の入力欄：返信する相手（コメント・返信の ID）と、最初に入れておく文字
  const [replying, setReplying] = useState<{ targetId: number; body: string } | null>(null)

  const more = useMutation({
    mutationFn: () => fetchReplies(comment.id, cursor ?? undefined),
    onSuccess: (page) => {
      setLoaded((current) => [...current, ...page.items])
      setCursor(page.nextCursor)
    },
    onError: (err) => toast(errorMessage(err)),
  })

  const reply = useMutation({
    mutationFn: (target: { targetId: number; body: string }) =>
      postComment(workoutId, target.body, target.targetId),
    onSuccess: (result) => {
      setPosted((current) => [...current, result.comment as Reply])
      setReplying(null)
      onReplied(result.commentCount)
    },
    onError: (err) => toast(errorMessage(err)),
  })

  const fromServer = mergeById(comment.replies, loaded)
  const replies = mergeById(fromServer, posted).filter((r) => !deleted.has(r.id))
  const remaining = remainingReplies(comment.replyCount, fromServer.length)

  return (
    <div className={styles.thread}>
      <Entry
        item={comment}
        mention={null}
        onReply={() => setReplying({ targetId: comment.id, body: '' })}
        onDeleted={(count) => onDeleted(comment.id, count)}
        hasReplies
      />
      {replies.length > 0 && (
        <ul className={styles.replies}>
          {replies.map((r) => (
            <li key={r.id}>
              <Entry
                item={r}
                mention={mentionToShow(r)}
                onReply={() =>
                  setReplying({ targetId: r.id, body: replyPrefix(r.author.username) })
                }
                onDeleted={(count) => onDeleted(r.id, count)}
                hasReplies={false}
              />
            </li>
          ))}
        </ul>
      )}
      {cursor !== null && remaining > 0 && (
        <button
          type="button"
          className={styles.moreReplies}
          disabled={more.isPending}
          onClick={() => more.mutate()}
        >
          返信をさらに表示（{remaining}件）
        </button>
      )}
      {replying && (
        <div className={styles.replyForm}>
          <CommentForm
            label="返信を入力"
            submitLabel="返信する"
            value={replying.body}
            onChange={(body) => setReplying({ ...replying, body })}
            onSubmit={() => reply.mutate(replying)}
            busy={reply.isPending}
            focusOnOpen
          />
        </div>
      )}
    </div>
  )
}

/** コメント・返信の1件：アイコン・表示名・@ユーザー名・時刻・本文と、「返信」「削除」 */
function Entry({
  item,
  mention,
  onReply,
  onDeleted,
  hasReplies,
}: {
  item: Comment | Reply
  /** 本文の前に出す返信先 */
  mention: string | null
  onReply: () => void
  onDeleted: (commentCount: number) => void
  /** 返信元か（消すと返信も消える） */
  hasReplies: boolean
}) {
  const toast = useToast()
  const [confirming, setConfirming] = useState(false)
  // 「◯分前」の基準。描き直すたびに変わらないよう、出したときの時刻を持つ
  const [now] = useState(() => new Date())
  const removal = useMutation({
    mutationFn: () => deleteComment(item.id),
    onSuccess: (result) => {
      setConfirming(false)
      toast('コメントを削除しました')
      onDeleted(result.commentCount)
    },
    onError: (err) => {
      setConfirming(false)
      toast(errorMessage(err))
    },
  })
  const author: UserSummary = item.author

  return (
    <article className={styles.entry}>
      <UserLink username={author.username} plain>
        <Avatar displayName={author.displayName} iconUrl={author.iconUrl} size={32} />
        <span className="visually-hidden">{author.displayName}のプロフィール</span>
      </UserLink>
      <div className={styles.main}>
        <p className={styles.head}>
          <UserLink username={author.username} className={styles.displayName}>
            {author.displayName}
          </UserLink>
          <UserLink username={author.username} className={styles.muted}>
            @{author.username}
          </UserLink>
          <span className={styles.muted}>· {formatNotificationTime(item.createdAt, now)}</span>
        </p>
        <p className={styles.body}>
          {mention && (
            <>
              <UserLink username={mention} className={styles.mention}>
                @{mention}
              </UserLink>{' '}
            </>
          )}
          {item.body}
        </p>
        <div className={styles.actions}>
          <button type="button" className={styles.action} onClick={onReply}>
            返信
          </button>
          {item.deletable && (
            <button type="button" className={styles.danger} onClick={() => setConfirming(true)}>
              削除
            </button>
          )}
        </div>
      </div>
      {confirming && (
        <ConfirmDialog
          title="このコメントを削除しますか？"
          description={hasReplies ? '返信も削除されます' : undefined}
          confirmLabel="削除する"
          busy={removal.isPending}
          onConfirm={() => removal.mutate()}
          onCancel={() => setConfirming(false)}
        />
      )}
    </article>
  )
}
