import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-01 ログイン（docs/screens.md）。状態：入力中 → 送信中 → 成功（ホームへ）／失敗（文言を出し、入力は残す）

beforeEach(() => {
  startAs(null)
})

async function openLogin() {
  renderApp('/login')
  return {
    email: await screen.findByLabelText('メールアドレス'),
    password: screen.getByLabelText('パスワード'),
    submit: screen.getByRole('button', { name: 'ログイン' }),
  }
}

test('空のまま送ると、項目ごとのエラーを出し、API を呼ばない', async () => {
  const { email, submit } = await openLogin()

  await userEvent.click(submit)

  expect(screen.getByText('メールアドレスを入力してください')).toBeInTheDocument()
  expect(screen.getByText('パスワードを入力してください')).toBeInTheDocument()
  expect(email).toHaveAttribute('aria-invalid', 'true')
  expect(api.login).not.toHaveBeenCalled()
})

test('メールアドレスの前後の空白を除いて送り、ログインできたらホームへ', async () => {
  vi.mocked(api.login).mockResolvedValue({ accessToken: 'access', user: ME })
  const { email, password, submit } = await openLogin()

  await userEvent.type(email, ` ${ME.email} `)
  await userEvent.type(password, 'password123')
  await userEvent.click(submit)

  expect(await screen.findByRole('heading', { level: 1, name: 'ホーム' })).toBeInTheDocument()
  expect(api.login).toHaveBeenCalledWith({ email: ME.email, password: 'password123' })
})

test.each([
  [
    'パスワードが違う',
    401,
    'INVALID_CREDENTIALS',
    'メールアドレスまたはパスワードが正しくありません',
  ],
  ['失敗が続いてロック中', 429, 'TOO_MANY_LOGIN_ATTEMPTS', 'しばらくログインできません'],
  ['混み合っている', 503, 'SERVER_BUSY', 'ただいま混み合っています'],
])('%s：サーバーの文言を出し、入力は残す', async (_, status, code, message) => {
  vi.mocked(api.login).mockRejectedValue(new api.ApiError(status, code, message))
  const { email, password, submit } = await openLogin()

  await userEvent.type(email, ME.email)
  await userEvent.type(password, 'wrong-password')
  await userEvent.click(submit)

  expect(await screen.findByRole('alert')).toHaveTextContent(message)
  expect(email).toHaveValue(ME.email)
  expect(password).toHaveValue('wrong-password')
  // もう一度送れる
  expect(screen.getByRole('button', { name: 'ログイン' })).toBeEnabled()
})

test('送っている間はボタンを押せない（2回送らない）', async () => {
  vi.mocked(api.login).mockReturnValue(new Promise(() => {}))
  const { email, password, submit } = await openLogin()

  await userEvent.type(email, ME.email)
  await userEvent.type(password, 'password123')
  await userEvent.click(submit)

  expect(screen.getByRole('button', { name: 'ログイン中…' })).toBeDisabled()
})

test('新規登録の画面へ移れる', async () => {
  await openLogin()

  await userEvent.click(screen.getByRole('link', { name: '新規登録はこちら' }))

  expect(await screen.findByRole('heading', { level: 1, name: '新規登録' })).toBeInTheDocument()
})
