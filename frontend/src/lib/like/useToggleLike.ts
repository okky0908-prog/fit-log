import { useMutation, useQueryClient } from '@tanstack/react-query'
import { errorMessage, likeWorkout, unlikeWorkout } from '../api/client'
import type { LikeState } from '../api/types'
import { useToast } from '../toast'
import { applyWorkoutValues, workoutQueryFilters } from '../workout/workoutCache'
import { toggleLike } from './like'

/**
 * ナイストレーニングを付ける・取り消す（F-40〜F-42）。押した瞬間に、読み込み済みのデータを書き換えて画面を先に変える。
 * 送り終えたら返ってきた値に合わせ、失敗したら押す前のデータに戻してお知らせで理由を出す。
 * どの画面で押しても、同じ記録を出しているほかの画面の表示も同じ値になる
 */
export function useToggleLike(workoutId: number) {
  const queryClient = useQueryClient()
  const toast = useToast()

  const apply = (state: LikeState) => applyWorkoutValues(queryClient, workoutId, state)

  return useMutation({
    mutationFn: (current: LikeState) =>
      current.likedByMe ? unlikeWorkout(workoutId) : likeWorkout(workoutId),
    onMutate: async (current: LikeState) => {
      // 読み込み中の取り直しが、先に変えた表示を古い値で上書きしないよう止める
      await Promise.all(
        workoutQueryFilters(workoutId).map((filters) => queryClient.cancelQueries(filters)),
      )
      const snapshot = workoutQueryFilters(workoutId).flatMap((filters) =>
        queryClient.getQueriesData(filters),
      )
      apply(toggleLike(current))
      return { snapshot }
    },
    onSuccess: (state) => apply(state),
    onError: (err, _current, context) => {
      context?.snapshot.forEach(([key, data]) => queryClient.setQueryData(key, data))
      toast(errorMessage(err))
    },
  })
}
