import type { LikeState } from '../api/types'

/*
 * ナイストレーニングのボタン（docs/screens.md「投稿カード」、F-40〜F-42）。
 * 押した瞬間に画面を先に変えるため、読み込み済みのデータ（TanStack Query のキャッシュ）の中の数と「付けたか」を書き換える。
 * 記録の詳細（Workout）と、一覧のページ（タイムライン・プロフィールの記録一覧。useInfiniteQuery の pages）の両方を扱う
 */

/** 押したあとの状態（押した瞬間に画面に出す値） */
export function toggleLike(state: LikeState): LikeState {
  return state.likedByMe
    ? { likeCount: state.likeCount - 1, likedByMe: false }
    : { likeCount: state.likeCount + 1, likedByMe: true }
}

/** 記録（詳細・一覧の1件）の形。ナイストレーニングの項目を持つものだけを書き換える */
interface WithLikes extends LikeState {
  id: number
}

function isWorkout(value: unknown): value is WithLikes {
  return typeof value === 'object' && value !== null && 'id' in value && 'likeCount' in value
}

function isPages(value: unknown): value is { pages: { items: unknown[] }[] } {
  return typeof value === 'object' && value !== null && 'pages' in value
}

/**
 * 読み込み済みのデータの中の、その記録の数と付けたかを書き換える。変わらない部分は同じものを返す（描き直しを減らすため）。
 * 記録でないデータ（プロフィール・ユーザーの一覧）と、まだないデータはそのまま返す
 */
export function withLikeState<T>(data: T, workoutId: number, state: LikeState): T {
  if (isWorkout(data)) {
    return data.id === workoutId ? { ...data, ...state } : data
  }
  if (isPages(data)) {
    let changed = false
    const pages = data.pages.map((page) => {
      if (!page.items.some((item) => isWorkout(item) && item.id === workoutId)) return page
      changed = true
      return {
        ...page,
        items: page.items.map((item) =>
          isWorkout(item) && item.id === workoutId ? { ...item, ...state } : item,
        ),
      }
    })
    return changed ? { ...data, pages } : data
  }
  return data
}
