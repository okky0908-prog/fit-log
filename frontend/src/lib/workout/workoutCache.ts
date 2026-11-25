import type { QueryClient, QueryFilters } from '@tanstack/react-query'
import type { LikeState } from '../api/types'

/*
 * 読み込み済みのデータ（TanStack Query のキャッシュ）の中の、記録の値を書き換える。
 * ナイストレーニング・コメントの操作のあとに、同じ記録を出しているほかの画面（タイムライン・プロフィールの記録一覧・投稿詳細）の
 * 表示も同じ値にするために使う。記録の詳細（Workout）と、一覧のページ（useInfiniteQuery の pages）の両方を扱う
 */

/** 書き換える値 */
export type WorkoutValues = Partial<LikeState & { commentCount: number }>

/** その記録を出しうる、読み込み済みのデータ（タイムライン・プロフィールの記録一覧・記録の詳細） */
export function workoutQueryFilters(workoutId: number): QueryFilters[] {
  return [{ queryKey: ['timeline'] }, { queryKey: ['users'] }, { queryKey: ['workout', workoutId] }]
}

/** 記録（詳細・一覧の1件）の形。ナイストレーニングの項目を持つものだけを書き換える */
interface WorkoutLike extends LikeState {
  id: number
}

function isWorkout(value: unknown): value is WorkoutLike {
  return typeof value === 'object' && value !== null && 'id' in value && 'likeCount' in value
}

function isPages(value: unknown): value is { pages: { items: unknown[] }[] } {
  return typeof value === 'object' && value !== null && 'pages' in value
}

/**
 * 読み込み済みのデータの中の、その記録の値（ナイストレーニングの数・付けたか・コメント数）を書き換える。
 * 変わらない部分は同じものを返す（描き直しを減らすため）。記録でないデータ（プロフィール・ユーザーの一覧）と、まだないデータはそのまま返す
 */
export function withWorkoutValues<T>(data: T, workoutId: number, values: WorkoutValues): T {
  if (isWorkout(data)) {
    return data.id === workoutId ? { ...data, ...values } : data
  }
  if (isPages(data)) {
    let changed = false
    const pages = data.pages.map((page) => {
      if (!page.items.some((item) => isWorkout(item) && item.id === workoutId)) return page
      changed = true
      return {
        ...page,
        items: page.items.map((item) =>
          isWorkout(item) && item.id === workoutId ? { ...item, ...values } : item,
        ),
      }
    })
    return changed ? { ...data, pages } : data
  }
  return data
}

/** 読み込み済みのすべてのデータ（タイムライン・プロフィールの記録一覧・記録の詳細）の、その記録の値を書き換える */
export function applyWorkoutValues(
  queryClient: QueryClient,
  workoutId: number,
  values: WorkoutValues,
): void {
  for (const filters of workoutQueryFilters(workoutId)) {
    queryClient.setQueriesData(filters, (data: unknown) =>
      withWorkoutValues(data, workoutId, values),
    )
  }
}
