import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// 未確認のときの案内（docs/screens.md「ナビゲーション」、F-05）。ログイン後の画面のナビゲーションの下に出す

const UNVERIFIED = { ...ME, emailVerified: false }
const NOTICE = 'メールアドレスの確認が済んでいません。届いたメールのリンクを開いてください'

beforeEach(() => {
  localStorage.clear()
  vi.mocked(api.resendVerificationEmail).mockReset()
})

async function openHome() {
  renderApp('/')
  await screen.findByRole('heading', { level: 1, name: 'ホーム' })
}

test('未確認なら案内を出す', async () => {
  startAs(UNVERIFIED)

  await openHome()

  expect(screen.getByText(NOTICE)).toBeInTheDocument()
})

test('確認済みなら出さない', async () => {
  startAs(ME)

  await openHome()

  expect(screen.queryByText(NOTICE)).not.toBeInTheDocument()
})

test('再送を押すと送り、送ったことを伝える。送っている間は押せない', async () => {
  startAs(UNVERIFIED)
  let finish!: () => void
  vi.mocked(api.resendVerificationEmail).mockReturnValue(
    new Promise((resolve) => {
      finish = () => resolve(undefined)
    }),
  )
  await openHome()

  await userEvent.click(screen.getByRole('button', { name: '確認メールを再送' }))
  expect(screen.getByRole('button', { name: '送信中…' })).toBeDisabled()
  finish()

  expect(await screen.findByText('確認メールを送りました')).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '確認メールを再送' })).toBeEnabled()
})

test('送信の上限を超えたら、サーバーの文言を伝える', async () => {
  startAs(UNVERIFIED)
  vi.mocked(api.resendVerificationEmail).mockRejectedValue(
    new api.ApiError(429, 'TOO_MANY_EMAILS', 'メールの送信が続いたため、しばらく送れません'),
  )
  await openHome()

  await userEvent.click(screen.getByRole('button', { name: '確認メールを再送' }))

  expect(
    await screen.findByText('メールの送信が続いたため、しばらく送れません'),
  ).toBeInTheDocument()
})

/** 別の端末で確認を済ませていた。ユーザーを取り直して案内を消す */
test('すでに確認済みと言われたら、ユーザーを取り直して案内を消す', async () => {
  startAs(UNVERIFIED)
  vi.mocked(api.resendVerificationEmail).mockRejectedValue(
    new api.ApiError(409, 'ALREADY_VERIFIED', 'メールアドレスの確認はすでに済んでいます'),
  )
  vi.mocked(api.fetchMe).mockResolvedValue(ME)
  await openHome()

  await userEvent.click(screen.getByRole('button', { name: '確認メールを再送' }))

  expect(await screen.findByText('メールアドレスの確認はすでに済んでいます')).toBeInTheDocument()
  expect(screen.queryByText(NOTICE)).not.toBeInTheDocument()
})

test('閉じたら消え、開き直しても今日は出さない', async () => {
  startAs(UNVERIFIED)
  const first = renderApp('/')
  await screen.findByText(NOTICE)

  await userEvent.click(screen.getByRole('button', { name: '案内を閉じる' }))
  expect(screen.queryByText(NOTICE)).not.toBeInTheDocument()
  first.unmount()

  await openHome()
  expect(screen.queryByText(NOTICE)).not.toBeInTheDocument()
})
