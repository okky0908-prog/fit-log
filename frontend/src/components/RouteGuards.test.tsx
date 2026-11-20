import { act, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import { LoginPage } from '../pages/LoginPage'
import { ME, renderApp, startAs } from '../test/helpers'
import { PublicOnly, RequireAuth } from './RouteGuards'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// ログインが必要な画面に未ログインで来たら、ログインしたあと、もとの画面に戻る（docs/screens.md）
const ROUTES = [
  { element: <PublicOnly />, children: [{ path: '/login', element: <LoginPage /> }] },
  {
    element: <RequireAuth />,
    children: [
      { path: '/', element: <h1>ホーム</h1> },
      { path: '/records', element: <h1>記録</h1> },
    ],
  },
]

test('ログインが必要な画面から来たら、ログインしたあとに、もとの画面（クエリを含む）に戻る', async () => {
  startAs(null)
  vi.mocked(api.login).mockResolvedValue({ accessToken: 'access', user: ME })
  const { router } = renderApp('/records?week=2026-11-16', { routeObjects: ROUTES })

  await userEvent.type(await screen.findByLabelText('メールアドレス'), ME.email)
  await userEvent.type(screen.getByLabelText('パスワード'), 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

  expect(await screen.findByRole('heading', { name: '記録' })).toBeInTheDocument()
  expect(router.state.location.search).toBe('?week=2026-11-16')
})

test('直接ログイン画面を開いてログインしたら、ホームへ', async () => {
  startAs(null)
  vi.mocked(api.login).mockResolvedValue({ accessToken: 'access', user: ME })
  renderApp('/login', { routeObjects: ROUTES })

  await userEvent.type(await screen.findByLabelText('メールアドレス'), ME.email)
  await userEvent.type(screen.getByLabelText('パスワード'), 'password123')
  await userEvent.click(screen.getByRole('button', { name: 'ログイン' }))

  expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
})

test('ログイン中に期限が切れて未ログインになったら、ログイン画面へ', async () => {
  startAs(ME)
  renderApp('/records', { routeObjects: ROUTES })
  await screen.findByRole('heading', { name: '記録' })

  const handler = vi.mocked(api.setUnauthorizedHandler).mock.calls[0][0]
  act(() => handler?.())

  expect(await screen.findByRole('heading', { name: 'ログイン' })).toBeInTheDocument()
})
