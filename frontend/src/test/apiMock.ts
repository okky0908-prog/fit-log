import { vi } from 'vitest'
import type * as api from '../lib/api/client'

/**
 * 通信の部品の偽物。エラーの型（ApiError）などはそのまま使い、通信する関数だけを偽物にする。
 * vi.mock はファイルの先頭に移されるので、ほかの import より先に読めるよう、この関数は別のファイルに置いて動的に読む：
 *
 *   vi.mock('../lib/api/client', (original) => import('../test/apiMock').then((m) => m.mockApiClient(original)))
 */
const EMPTY_PAGE = { items: [], nextCursor: null, hasNext: false }

export async function mockApiClient(importOriginal: () => Promise<typeof api>) {
  const actual = await importOriginal()
  return {
    ...actual,
    refresh: vi.fn<typeof actual.refresh>(),
    login: vi.fn<typeof actual.login>(),
    signup: vi.fn<typeof actual.signup>(),
    logout: vi.fn<typeof actual.logout>(),
    fetchMe: vi.fn<typeof actual.fetchMe>(),
    verifyEmail: vi.fn<typeof actual.verifyEmail>(),
    resendVerificationEmail: vi.fn<typeof actual.resendVerificationEmail>(),
    setUnauthorizedHandler: vi.fn<typeof actual.setUnauthorizedHandler>(),
    fetchWorkout: vi.fn<typeof actual.fetchWorkout>(),
    deleteWorkout: vi.fn<typeof actual.deleteWorkout>(),
    likeWorkout: vi.fn<typeof actual.likeWorkout>(),
    unlikeWorkout: vi.fn<typeof actual.unlikeWorkout>(),
    // 投稿詳細は多くの画面のテストで開くので、コメント一覧の既定は「0件」にしておく
    fetchComments: vi.fn<typeof actual.fetchComments>(async () => EMPTY_PAGE),
    postComment: vi.fn<typeof actual.postComment>(),
    deleteComment: vi.fn<typeof actual.deleteComment>(),
    fetchReplies: vi.fn<typeof actual.fetchReplies>(),
    createWorkout: vi.fn<typeof actual.createWorkout>(),
    updateWorkout: vi.fn<typeof actual.updateWorkout>(),
    searchExercises: vi.fn<typeof actual.searchExercises>(),
    fetchRecentExercises: vi.fn<typeof actual.fetchRecentExercises>(),
    createExercise: vi.fn<typeof actual.createExercise>(),
    updateExercise: vi.fn<typeof actual.updateExercise>(),
    deleteExercise: vi.fn<typeof actual.deleteExercise>(),
    fetchNotifications: vi.fn<typeof actual.fetchNotifications>(),
    // ナビゲーションがどの画面でも呼ぶので、既定は「未読 0件」にしておく
    fetchUnreadCount: vi.fn<typeof actual.fetchUnreadCount>(async () => ({
      unreadCount: 0,
      followRequestCount: 0,
    })),
    markNotificationsRead: vi.fn<typeof actual.markNotificationsRead>(),
    followUser: vi.fn<typeof actual.followUser>(),
    unfollowUser: vi.fn<typeof actual.unfollowUser>(),
    fetchFollowing: vi.fn<typeof actual.fetchFollowing>(),
    fetchFollowers: vi.fn<typeof actual.fetchFollowers>(),
    fetchProfile: vi.fn<typeof actual.fetchProfile>(),
    updateProfile: vi.fn<typeof actual.updateProfile>(),
    // ホーム・プロフィールは多くの画面のテストで開く（保存のあとに移るなど）ので、既定は「0件」にしておく
    fetchTimeline: vi.fn<typeof actual.fetchTimeline>(async () => EMPTY_PAGE),
    fetchNewPostCount: vi.fn<typeof actual.fetchNewPostCount>(async () => ({ count: 0 })),
    fetchUserWorkouts: vi.fn<typeof actual.fetchUserWorkouts>(async () => EMPTY_PAGE),
    fetchWeeklyStats: vi.fn<typeof actual.fetchWeeklyStats>(),
    fetchMonthlyStats: vi.fn<typeof actual.fetchMonthlyStats>(),
    fetchCalendar: vi.fn<typeof actual.fetchCalendar>(),
    fetchDayWorkouts: vi.fn<typeof actual.fetchDayWorkouts>(),
    fetchRunningStats: vi.fn<typeof actual.fetchRunningStats>(),
    fetchUsedExercises: vi.fn<typeof actual.fetchUsedExercises>(),
    fetchExerciseTrend: vi.fn<typeof actual.fetchExerciseTrend>(),
    putBodyRecord: vi.fn<typeof actual.putBodyRecord>(),
    fetchBodyRecords: vi.fn<typeof actual.fetchBodyRecords>(),
    deleteBodyRecord: vi.fn<typeof actual.deleteBodyRecord>(),
    fetchBodyTrend: vi.fn<typeof actual.fetchBodyTrend>(),
  }
}
