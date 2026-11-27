import { Navigate, type RouteObject } from 'react-router'
import { AppLayout } from '../components/AppLayout'
import { PublicOnly, RequireAuth } from '../components/RouteGuards'
import { ErrorPage } from './ErrorPage'
import { FollowListPage } from './FollowListPage'
import { HomePage } from './HomePage'
import { LoginPage } from './LoginPage'
import { NotificationsPage } from './NotificationsPage'
import { ProfileEditPage } from './ProfileEditPage'
import { ProfilePage } from './ProfilePage'
import { RecordsPage } from './RecordsPage'
import { RunningPage } from './RunningPage'
import { SignupPage } from './SignupPage'
import { VerifyEmailPage } from './VerifyEmailPage'
import { WorkoutDetailPage } from './WorkoutDetailPage'
import { WorkoutEditorPage } from './WorkoutEditorPage'

// 画面の URL は docs/screens.md の画面一覧に合わせる。
// 一覧（routes）とブラウザ用の切り替え（main.tsx の createBrowserRouter）を分けているのは、
// テストで同じ一覧から好きな URL で始められるようにするため（routes.test.tsx）
export const routes: RouteObject[] = [
  {
    // 表示中にエラーが起きたら、真っ白な画面の代わりに ErrorPage を出す
    errorElement: <ErrorPage />,
    children: [
      {
        // 未ログインの人の画面。ログイン済みならホーム（か、もとの画面）へ
        element: <PublicOnly />,
        children: [
          { path: '/login', element: <LoginPage /> },
          { path: '/signup', element: <SignupPage /> },
        ],
      },
      // ログインしていてもしていなくても開ける（別の端末でメールを開くことがあるため）
      { path: '/verify-email', element: <VerifyEmailPage /> },
      {
        // ログインが必要な画面（ナビゲーション付き）
        element: <RequireAuth />,
        children: [
          {
            element: <AppLayout />,
            children: [
              { path: '/', element: <HomePage key="following" kind="following" /> },
              { path: '/all', element: <HomePage key="all" kind="all" /> },
              { path: '/workouts/new', element: <WorkoutEditorPage /> },
              { path: '/workouts/:workoutId', element: <WorkoutDetailPage /> },
              { path: '/workouts/:workoutId/edit', element: <WorkoutEditorPage /> },
              { path: '/notifications', element: <NotificationsPage /> },
              { path: '/records', element: <RecordsPage key="week" kind="week" /> },
              { path: '/records/month', element: <RecordsPage key="month" kind="month" /> },
              { path: '/records/running', element: <RunningPage /> },
              { path: '/users/:username', element: <ProfilePage /> },
              { path: '/settings/profile', element: <ProfileEditPage /> },
              {
                path: '/users/:username/following',
                element: <FollowListPage key="following" kind="following" />,
              },
              {
                path: '/users/:username/followers',
                element: <FollowListPage key="followers" kind="followers" />,
              },
            ],
          },
        ],
      },
      // 一覧にない URL はホームへ（ログインしていなければ、ホームの守りがログイン画面へ移す）
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]
