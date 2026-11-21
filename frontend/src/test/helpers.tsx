import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import { StrictMode } from 'react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { vi } from 'vitest'
import { ToastProvider } from '../components/Toast'
import * as api from '../lib/api/client'
import type { Me } from '../lib/api/types'
import { AuthProvider } from '../lib/auth/AuthProvider'
import { createQueryClient } from '../lib/queryClient'
import { routes } from '../pages/routes'

// 画面のテストの共通の組み立て（docs/test-plan.md 7章）。
// API は偽物にする（test/apiMock.ts の説明のとおり、テストのファイルの先頭で vi.mock を呼ぶ）

export const ME: Me = {
  username: 'yamada',
  displayName: '山田太郎',
  iconUrl: null,
  bio: null,
  email: 'yamada@example.com',
  emailVerified: true,
  isPrivate: false,
  weightUnit: 'kg',
  distanceUnit: 'km',
}

/** 開いたときのログインの状態。null なら未ログイン（Cookie での再発行が失敗する） */
export function startAs(user: Me | null): void {
  if (user) {
    vi.mocked(api.refresh).mockResolvedValue({ accessToken: 'access', user })
  } else {
    vi.mocked(api.refresh).mockRejectedValue(
      new api.ApiError(401, 'SESSION_EXPIRED', 'ログインの有効期限が切れました'),
    )
  }
}

interface RenderOptions {
  /** 画面の一覧。省略したらアプリの一覧（pages/routes.tsx） */
  routeObjects?: RouteObject[]
  /** React の StrictMode で描く（開発時と同じく、作り直しを2回行う） */
  strict?: boolean
}

/** アプリを、指定した URL から始める。TanStack Query はテストごとに新しく作る */
export function renderApp(
  path: string,
  { routeObjects = routes, strict = false }: RenderOptions = {},
) {
  const router = createMemoryRouter(routeObjects, { initialEntries: [path] })
  const tree = (
    <QueryClientProvider client={createQueryClient()}>
      <ToastProvider>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </ToastProvider>
    </QueryClientProvider>
  )
  return { router, ...render(strict ? <StrictMode>{tree}</StrictMode> : tree) }
}
