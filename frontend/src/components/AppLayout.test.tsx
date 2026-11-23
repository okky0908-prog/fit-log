import { act, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// ナビゲーション（docs/screens.md「ナビゲーション」）。検索・記録（ダッシュボード）・目標・プロフィールなどの行き先は、その画面を作るときに足す

beforeEach(() => {
  vi.mocked(api.fetchUnreadCount).mockReset()
  vi.mocked(api.fetchUnreadCount).mockResolvedValue({ unreadCount: 3, followRequestCount: 0 })
  startAs(ME)
})

afterEach(() => {
  vi.useRealTimers()
})

test('ホーム・記録する・通知へ行ける', async () => {
  renderApp('/')

  const nav = await screen.findByRole('navigation', { name: 'メイン' })
  expect(within(nav).getByRole('link', { name: 'ホーム' })).toHaveAttribute('href', '/')
  // 「＋」は飾りなので、読み上げの名前に入れない
  expect(within(nav).getByRole('link', { name: '記録する' })).toHaveAttribute(
    'href',
    '/workouts/new',
  )
  expect(await screen.findByRole('link', { name: '通知（未読 3件）' })).toHaveAttribute(
    'href',
    '/notifications',
  )
})

test('未読が 0 なら数を出さず、100 以上は「99+」', async () => {
  vi.mocked(api.fetchUnreadCount).mockResolvedValueOnce({ unreadCount: 0, followRequestCount: 0 })
  const first = renderApp('/')
  expect(await screen.findByRole('link', { name: '通知' })).toBeInTheDocument()
  expect(screen.queryByText('0')).not.toBeInTheDocument()
  first.unmount()

  vi.mocked(api.fetchUnreadCount).mockResolvedValueOnce({ unreadCount: 100, followRequestCount: 0 })
  renderApp('/')

  const bell = await screen.findByRole('link', { name: '通知（未読 99件以上）' })
  expect(bell).toHaveTextContent('99+')
})

test('未読の数は60秒ごとに取り直す', async () => {
  vi.useFakeTimers({ shouldAdvanceTime: true })
  renderApp('/')
  await screen.findByRole('link', { name: '通知（未読 3件）' })
  vi.mocked(api.fetchUnreadCount).mockResolvedValue({ unreadCount: 5, followRequestCount: 0 })

  await act(() => vi.advanceTimersByTimeAsync(60_000))

  expect(await screen.findByRole('link', { name: '通知（未読 5件）' })).toBeInTheDocument()
})

test('ブラウザのタブが表に戻ったときも取り直す', async () => {
  renderApp('/')
  await screen.findByRole('link', { name: '通知（未読 3件）' })
  vi.mocked(api.fetchUnreadCount).mockResolvedValue({ unreadCount: 7, followRequestCount: 0 })

  act(() => {
    window.dispatchEvent(new Event('visibilitychange'))
  })

  expect(await screen.findByRole('link', { name: '通知（未読 7件）' })).toBeInTheDocument()
})

test('未読の数を取れなくても、ほかの部分は使える（数を出さない）', async () => {
  vi.mocked(api.fetchUnreadCount).mockRejectedValue(
    new api.ApiError(400, 'VALIDATION_FAILED', 'エラー'),
  )
  renderApp('/')

  expect(await screen.findByRole('link', { name: '通知' })).toBeInTheDocument()
  expect(screen.getByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
})

test('アイコンのメニューから自分のプロフィールへ移る', async () => {
  vi.mocked(api.fetchProfile).mockReturnValue(new Promise(() => {}))
  const { router } = renderApp('/')

  await userEvent.click(await screen.findByRole('button', { name: '自分のメニュー' }))
  await userEvent.click(screen.getByRole('menuitem', { name: 'プロフィール' }))

  expect(router.state.location.pathname).toBe(`/users/${ME.username}`)
  expect(screen.queryByRole('menu')).not.toBeInTheDocument()
})

test('アイコンのメニューからログアウトする', async () => {
  vi.mocked(api.logout).mockResolvedValue(undefined)
  renderApp('/')

  await userEvent.click(await screen.findByRole('button', { name: '自分のメニュー' }))
  await userEvent.click(screen.getByRole('menuitem', { name: 'ログアウト' }))

  expect(await screen.findByRole('heading', { level: 1, name: 'ログイン' })).toBeInTheDocument()
})
