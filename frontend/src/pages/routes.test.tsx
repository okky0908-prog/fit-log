import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import { ME, renderApp, startAs } from '../test/helpers'
import { routes } from './routes'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// 画面の切り替えと守り（docs/screens.md「画面一覧」の下の決まり）。開いた URL × ログインの状態 → 表示する画面

beforeEach(() => {
  vi.mocked(api.verifyEmail).mockResolvedValue({ result: 'verified', email: ME.email })
})

describe.each([
  // [開いた URL, ログイン中か, 表示する画面の見出し]
  ['/', false, 'ログイン'],
  ['/', true, 'ホーム'],
  ['/login', false, 'ログイン'],
  ['/login', true, 'ホーム'],
  ['/signup', false, '新規登録'],
  ['/signup', true, 'ホーム'],
  ['/verify-email?token=abc', false, 'メールアドレスの確認'],
  ['/verify-email?token=abc', true, 'メールアドレスの確認'],
  ['/no-such-page', false, 'ログイン'],
  ['/no-such-page', true, 'ホーム'],
])('%s をログイン中=%s で開く', (path, loggedIn, heading) => {
  test(`「${heading}」を表示する`, async () => {
    startAs(loggedIn ? ME : null)

    renderApp(path)

    expect(await screen.findByRole('heading', { level: 1, name: heading })).toBeInTheDocument()
  })
})

test('ログインの状態を確かめている間は、読み込み中を出す', () => {
  vi.mocked(api.refresh).mockReturnValue(new Promise(() => {}))

  renderApp('/')

  expect(screen.getByText('読み込み中…')).toBeInTheDocument()
})

test('アイコンのメニューのログアウトを押すと、ログイン画面へ', async () => {
  startAs(ME)
  vi.mocked(api.logout).mockResolvedValue(undefined)
  renderApp('/')

  await userEvent.click(await screen.findByRole('button', { name: '自分のメニュー' }))
  await userEvent.click(screen.getByRole('menuitem', { name: 'ログアウト' }))

  expect(await screen.findByRole('heading', { level: 1, name: 'ログイン' })).toBeInTheDocument()
  expect(api.logout).toHaveBeenCalledOnce()
})

test('表示中に想定外のエラーが起きたら、真っ白にせずエラーの画面を出す', async () => {
  startAs(ME)
  vi.spyOn(console, 'error').mockImplementation(() => {})
  function Broken(): never {
    throw new Error('表示の不具合')
  }
  const [root] = routes
  renderApp('/broken', {
    routeObjects: [
      { errorElement: root.errorElement, children: [{ path: '/broken', element: <Broken /> }] },
    ],
  })

  expect(
    await screen.findByRole('heading', { level: 1, name: 'エラーが発生しました' }),
  ).toBeInTheDocument()
  expect(screen.getByRole('link', { name: 'ホームへ' })).toHaveAttribute('href', '/')
})
