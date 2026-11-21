import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-02 新規登録（docs/screens.md）。入力チェックの境界は lib/validation/auth.test.ts で確かめる

beforeEach(() => {
  startAs(null)
})

async function fill(values: Partial<Record<string, string>> = {}) {
  renderApp('/signup')
  const input = {
    ユーザー名: 'yamada',
    表示名: '山田太郎',
    メールアドレス: ME.email,
    パスワード: 'password123',
    'パスワード（確認）': 'password123',
    ...values,
  }
  for (const [label, value] of Object.entries(input)) {
    const field = await screen.findByLabelText(label)
    if (value) await userEvent.type(field, value)
  }
}

async function submit() {
  await userEvent.click(screen.getByRole('button', { name: '登録する' }))
}

test('登録できたらホームへ移り、確認のメールを送ったことを伝える', async () => {
  vi.mocked(api.signup).mockResolvedValue({
    accessToken: 'access',
    user: { ...ME, emailVerified: false },
  })
  await fill()

  await submit()

  expect(await screen.findByRole('heading', { level: 1, name: 'ホーム' })).toBeInTheDocument()
  expect(screen.getByRole('status')).toHaveTextContent(
    '確認のメールを送りました。メールのリンクを開いて、メールアドレスの確認を済ませてください',
  )
  // パスワード（確認）はサーバーに送らない。メールアドレスは前後の空白を除く
  expect(api.signup).toHaveBeenCalledWith({
    username: 'yamada',
    displayName: '山田太郎',
    email: ME.email,
    password: 'password123',
  })
})

test('入力にエラーがあれば項目ごとに出し、API を呼ばない', async () => {
  await fill({ ユーザー名: 'abc', 'パスワード（確認）': 'password124' })

  await submit()

  expect(screen.getByLabelText('ユーザー名')).toHaveAccessibleDescription(
    'ユーザー名は半角英数字と_で4〜15文字で入力してください',
  )
  expect(screen.getByLabelText('パスワード（確認）')).toHaveAccessibleDescription(
    'パスワードが一致しません',
  )
  expect(api.signup).not.toHaveBeenCalled()
})

test('ユーザー名・メールアドレスが使われていたら、サーバーの文言をその項目に出す', async () => {
  vi.mocked(api.signup).mockRejectedValue(
    new api.ApiError(409, 'ALREADY_REGISTERED', 'すでに登録されています', [
      { field: 'username', message: 'このユーザー名はすでに使われています' },
      { field: 'email', message: 'このメールアドレスはすでに登録されています' },
    ]),
  )
  await fill()

  await submit()

  expect(await screen.findByLabelText('ユーザー名')).toHaveAccessibleDescription(
    'このユーザー名はすでに使われています',
  )
  expect(screen.getByLabelText('メールアドレス')).toHaveAccessibleDescription(
    'このメールアドレスはすでに登録されています',
  )
  expect(screen.queryByRole('alert')).not.toBeInTheDocument()
})

test('項目に結び付かないエラー（通信の失敗）は、上にまとめて出し、入力は残す', async () => {
  vi.mocked(api.signup).mockRejectedValue(
    new api.ApiError(0, 'NETWORK_ERROR', api.NETWORK_ERROR_MESSAGE),
  )
  await fill()

  await submit()

  expect(await screen.findByRole('alert')).toHaveTextContent(api.NETWORK_ERROR_MESSAGE)
  expect(screen.getByLabelText('ユーザー名')).toHaveValue('yamada')
  expect(screen.getByRole('button', { name: '登録する' })).toBeEnabled()
})

test('ログインの画面へ移れる', async () => {
  renderApp('/signup')

  await userEvent.click(await screen.findByRole('link', { name: 'ログインはこちら' }))

  expect(await screen.findByRole('heading', { level: 1, name: 'ログイン' })).toBeInTheDocument()
})
