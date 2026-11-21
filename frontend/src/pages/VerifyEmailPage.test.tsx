import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-05 メールアドレスの確認（docs/screens.md）。開くとすぐに A-06 を呼び、結果を出す。
// 結果（成功・リンクが使えない・通信の失敗）× ログインの状態 で、出すボタンが変わる

const UNVERIFIED = { ...ME, emailVerified: false }
const INVALID = new api.ApiError(400, 'INVALID_TOKEN', 'このリンクは使えません')

beforeEach(() => {
  vi.mocked(api.verifyEmail).mockReset()
  vi.mocked(api.fetchMe).mockResolvedValue(ME)
})

describe('成功', () => {
  beforeEach(() => {
    vi.mocked(api.verifyEmail).mockResolvedValue({ result: 'verified', email: ME.email })
  })

  test('ログインしていなければ、済んだことと「ログインへ」を出す', async () => {
    startAs(null)

    renderApp('/verify-email?token=abc')

    expect(await screen.findByText('メールアドレスの確認が済みました')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'ログインへ' })).toHaveAttribute('href', '/login')
    expect(api.verifyEmail).toHaveBeenCalledWith('abc')
  })

  test('ログイン中なら、ユーザーを取り直して（未確認の案内を消すため）「ホームへ」を出す', async () => {
    startAs(UNVERIFIED)

    renderApp('/verify-email?token=abc')

    expect(await screen.findByRole('link', { name: 'ホームへ' })).toHaveAttribute('href', '/')
    expect(api.fetchMe).toHaveBeenCalledOnce()
  })

  /** トークンは1回しか使えないので、開発時の作り直し（StrictMode）でも2回送らない */
  test('確認の API は1回だけ呼ぶ', async () => {
    startAs(null)

    renderApp('/verify-email?token=abc', { strict: true })

    await screen.findByText('メールアドレスの確認が済みました')
    expect(api.verifyEmail).toHaveBeenCalledOnce()
  })
})

describe('リンクが使えない（期限切れ・使用済み・不正）', () => {
  test('ログイン中で未確認なら、「確認メールを再送」を出し、押すと送る', async () => {
    startAs(UNVERIFIED)
    vi.mocked(api.verifyEmail).mockRejectedValue(INVALID)
    vi.mocked(api.resendVerificationEmail).mockResolvedValue(undefined)
    renderApp('/verify-email?token=old')

    expect(await screen.findByText('このリンクは使えません')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: '確認メールを再送' }))

    expect(api.resendVerificationEmail).toHaveBeenCalledOnce()
    expect(await screen.findByText('確認メールを送りました')).toBeInTheDocument()
  })

  test('ログインしていなければ、再送は出さず「ログインへ」を出す', async () => {
    startAs(null)
    vi.mocked(api.verifyEmail).mockRejectedValue(INVALID)
    renderApp('/verify-email?token=old')

    expect(await screen.findByText('このリンクは使えません')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '確認メールを再送' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'ログインへ' })).toBeInTheDocument()
  })

  test('確認済みのユーザーには、再送を出さず「ホームへ」を出す', async () => {
    startAs(ME)
    vi.mocked(api.verifyEmail).mockRejectedValue(INVALID)
    renderApp('/verify-email?token=old')

    expect(await screen.findByText('このリンクは使えません')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '確認メールを再送' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'ホームへ' })).toBeInTheDocument()
  })

  test('URL にトークンがなければ、API を呼ばずに使えないと出す', async () => {
    startAs(null)

    renderApp('/verify-email')

    expect(await screen.findByText('このリンクは使えません')).toBeInTheDocument()
    expect(api.verifyEmail).not.toHaveBeenCalled()
  })
})

test('通信に失敗したら、その文言と「もう一度試す」を出し、押すとやり直す', async () => {
  startAs(null)
  vi.mocked(api.verifyEmail)
    .mockRejectedValueOnce(new api.ApiError(0, 'NETWORK_ERROR', api.NETWORK_ERROR_MESSAGE))
    .mockResolvedValueOnce({ result: 'verified', email: ME.email })
  renderApp('/verify-email?token=abc')

  expect(await screen.findByText(api.NETWORK_ERROR_MESSAGE)).toBeInTheDocument()
  await userEvent.click(screen.getByRole('button', { name: 'もう一度試す' }))

  expect(await screen.findByText('メールアドレスの確認が済みました')).toBeInTheDocument()
  expect(api.verifyEmail).toHaveBeenCalledTimes(2)
})

/** トークンを含む URL が、ほかのサイトに渡らないように（docs/screens.md「画面一覧」の下の決まり） */
test('開いている間は Referrer-Policy を no-referrer にし、離れたら戻す', async () => {
  startAs(null)
  vi.mocked(api.verifyEmail).mockResolvedValue({ result: 'verified', email: ME.email })
  const { unmount } = renderApp('/verify-email?token=abc')
  await screen.findByText('メールアドレスの確認が済みました')

  expect(document.querySelector('meta[name="referrer"]')).toHaveAttribute('content', 'no-referrer')

  unmount()
  expect(document.querySelector('meta[name="referrer"]')).toBeNull()
})
