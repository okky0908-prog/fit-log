import type { LikeState } from '../api/types'

/*
 * ナイストレーニングのボタン（docs/screens.md「投稿カード」、F-40〜F-42）。
 * 押した瞬間に画面を先に変えるため、押したあとの値を作る（書き換えは workout/workoutCache.ts）
 */

/** 押したあとの状態（押した瞬間に画面に出す値） */
export function toggleLike(state: LikeState): LikeState {
  return state.likedByMe
    ? { likeCount: state.likeCount - 1, likedByMe: false }
    : { likeCount: state.likeCount + 1, likedByMe: true }
}
