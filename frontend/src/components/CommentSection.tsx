import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { type RefObject, useState } from 'react'
import { errorMessage, fetchComments, postComment } from '../lib/api/client'
import type { Comment, Visibility } from '../lib/api/types'
import { mergeById } from '../lib/comment/comment'
import { useToast } from '../lib/toast'
import { applyWorkoutValues } from '../lib/workout/workoutCache'
import { CommentForm } from './CommentForm'
import { CommentItem } from './CommentItem'
import styles from './CommentSection.module.css'

interface Props {
  workoutId: number
  visibility: Visibility
  /** 💬 を押したときにカーソルを合わせる入力欄 */
  inputRef: RefObject<HTMLTextAreaElement | null>
}

/**
 * S-12 のコメント欄（docs/screens.md S-12、F-30〜F-33）。返信元のコメントを古い順に20件ずつ（続きは「さらに表示」）、
 * その下に返信を3件まで出す。投稿したコメントはすぐ最後に足す（一覧を読み込み直さない。続きを読み込んでも重ねない）。
 * 投稿・削除のあとは、投稿詳細と一覧の 💬 の数を合わせる
 */
export function CommentSection({ workoutId, visibility, inputRef }: Props) {
  const queryClient = useQueryClient()
  const toast = useToast()
  const [body, setBody] = useState('')
  const [posted, setPosted] = useState<Comment[]>([])
  const [deleted, setDeleted] = useState<ReadonlySet<number>>(new Set())
  const query = useInfiniteQuery({
    queryKey: ['workout', workoutId, 'comments'],
    queryFn: ({ pageParam }) => fetchComments(workoutId, pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
  const setCommentCount = (commentCount: number) =>
    applyWorkoutValues(queryClient, workoutId, { commentCount })

  const post = useMutation({
    mutationFn: () => postComment(workoutId, body),
    onSuccess: (result) => {
      setPosted((current) => [...current, result.comment as Comment])
      setBody('')
      setCommentCount(result.commentCount)
    },
    onError: (err) => toast(errorMessage(err)),
  })

  const loaded = query.data?.pages.flatMap((page) => page.items) ?? []
  const items = mergeById(loaded, posted).filter((comment) => !deleted.has(comment.id))
  // 自分のみの記録には本人だけが書ける。自分へのメモとして見出しを変える
  const label = visibility === 'private' ? 'メモを書く' : 'コメントを入力'

  return (
    <section aria-labelledby="comments-title" className={styles.section}>
      <h2 id="comments-title" className={styles.title}>
        コメント
      </h2>
      <CommentForm
        label={label}
        submitLabel="コメントする"
        value={body}
        onChange={setBody}
        onSubmit={() => post.mutate()}
        busy={post.isPending}
        inputRef={inputRef}
      />
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
          <ul className={styles.list}>
            {items.map((comment) => (
              <li key={comment.id}>
                <CommentItem
                  workoutId={workoutId}
                  comment={comment}
                  deleted={deleted}
                  onDeleted={(id, commentCount) => {
                    setDeleted((current) => new Set(current).add(id))
                    setCommentCount(commentCount)
                  }}
                  onReplied={setCommentCount}
                />
              </li>
            ))}
          </ul>
          {query.hasNextPage && (
            <button
              type="button"
              className={`button-secondary ${styles.more}`}
              disabled={query.isFetchingNextPage}
              onClick={() => void query.fetchNextPage()}
            >
              {query.isFetchingNextPage ? '読み込み中…' : 'さらに表示'}
            </button>
          )}
        </>
      )}
    </section>
  )
}
