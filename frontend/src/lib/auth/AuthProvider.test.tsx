import { act, render, screen, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../api/client'
import type { AuthResponse, Me } from '../api/types'
import { AuthProvider } from './AuthProvider'
import { useAuth } from './authContext'

vi.mock('../api/client', (original) =>
  import('../../test/apiMock').then((m) => m.mockApiClient(original)),
)

const ME: Me = {
  username: 'yamada',
  displayName: '山田',
  iconUrl: null,
  bio: null,
  email: 'yamada@example.com',
  emailVerified: false,
  isPrivate: false,
  weightUnit: 'kg',
  distanceUnit: 'km',
}

function auth(user: Me = ME): AuthResponse {
  return { accessToken: 'access', user }
}

let current: ReturnType<typeof useAuth>

/** ログインの状態を画面に出し、操作は current から呼ぶ */
function Probe() {
  const value = useAuth()
  useEffect(() => {
    current = value
  })
  if (value.loading) return <p>確認中</p>
  return <p>{value.user ? `${value.user.username}:${value.user.emailVerified}` : '未ログイン'}</p>
}

function renderProvider() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>,
  )
}

beforeEach(() => {
  vi.mocked(api.refresh).mockReset()
  vi.mocked(api.login).mockReset()
  vi.mocked(api.signup).mockReset()
  vi.mocked(api.logout).mockReset()
  vi.mocked(api.fetchMe).mockReset()
  vi.mocked(api.setUnauthorizedHandler).mockReset()
})

describe('開いたときの復元', () => {
  test('Cookie で再発行できたら、ログイン中にする（それまでは確認中）', async () => {
    vi.mocked(api.refresh).mockResolvedValue(auth())

    renderProvider()

    expect(screen.getByText('確認中')).toBeInTheDocument()
    expect(await screen.findByText('yamada:false')).toBeInTheDocument()
  })

  test('再発行できなければ（Cookie がない・期限切れ・通信の失敗）、未ログインにする', async () => {
    vi.mocked(api.refresh).mockRejectedValue(new api.ApiError(401, 'SESSION_EXPIRED', '期限切れ'))

    renderProvider()

    expect(await screen.findByText('未ログイン')).toBeInTheDocument()
  })
})

test('復元が終わる前に閉じたら、終わったあとに状態を変えない', async () => {
  let finish!: (res: AuthResponse) => void
  vi.mocked(api.refresh).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  const errors = vi.spyOn(console, 'error').mockImplementation(() => {})
  const { unmount } = renderProvider()

  unmount()
  await act(async () => finish(auth()))

  expect(errors).not.toHaveBeenCalled()
})

describe('ログイン・新規登録・ログアウト', () => {
  beforeEach(() => {
    vi.mocked(api.refresh).mockRejectedValue(new Error('Cookie なし'))
  })

  test('ログインしたらログイン中にする', async () => {
    vi.mocked(api.login).mockResolvedValue(auth())
    renderProvider()
    await screen.findByText('未ログイン')

    await act(() => current.login({ email: 'yamada@example.com', password: 'password123' }))

    expect(screen.getByText('yamada:false')).toBeInTheDocument()
  })

  test('ログインに失敗したらエラーをそのまま投げ、未ログインのまま', async () => {
    const failure = new api.ApiError(401, 'INVALID_CREDENTIALS', '違います')
    vi.mocked(api.login).mockRejectedValue(failure)
    renderProvider()
    await screen.findByText('未ログイン')

    await expect(current.login({ email: 'a', password: 'b' })).rejects.toBe(failure)

    expect(screen.getByText('未ログイン')).toBeInTheDocument()
  })

  test('新規登録したらそのままログイン中にする', async () => {
    vi.mocked(api.signup).mockResolvedValue(auth())
    renderProvider()
    await screen.findByText('未ログイン')

    await act(() =>
      current.signup({
        username: 'yamada',
        displayName: '山田',
        email: 'yamada@example.com',
        password: 'password123',
      }),
    )

    expect(screen.getByText('yamada:false')).toBeInTheDocument()
  })

  test('ログアウトしたら未ログインにする。通信に失敗しても未ログインにする', async () => {
    vi.mocked(api.login).mockResolvedValue(auth())
    vi.mocked(api.logout).mockRejectedValue(new api.ApiError(0, 'NETWORK_ERROR', '失敗'))
    renderProvider()
    await screen.findByText('未ログイン')
    await act(() => current.login({ email: 'a', password: 'b' }))

    await act(() => current.logout())

    expect(api.logout).toHaveBeenCalledOnce()
    expect(screen.getByText('未ログイン')).toBeInTheDocument()
  })
})

describe('ログイン中のユーザーの取り直し・期限切れ', () => {
  test('取り直すと、メールアドレスの確認の状態などが新しくなる', async () => {
    vi.mocked(api.refresh).mockResolvedValue(auth())
    vi.mocked(api.fetchMe).mockResolvedValue({ ...ME, emailVerified: true })
    renderProvider()
    await screen.findByText('yamada:false')

    await act(() => current.reloadUser())

    expect(screen.getByText('yamada:true')).toBeInTheDocument()
  })

  test('未ログインなら取り直さない', async () => {
    vi.mocked(api.refresh).mockRejectedValue(new Error('Cookie なし'))
    renderProvider()
    await screen.findByText('未ログイン')

    await act(() => current.reloadUser())

    expect(api.fetchMe).not.toHaveBeenCalled()
  })

  test('通信の部品が「再発行もできない」と知らせたら、未ログインにする', async () => {
    vi.mocked(api.refresh).mockResolvedValue(auth())
    renderProvider()
    await screen.findByText('yamada:false')
    const handler = vi.mocked(api.setUnauthorizedHandler).mock.calls[0][0]

    act(() => handler?.())

    await waitFor(() => expect(screen.getByText('未ログイン')).toBeInTheDocument())
  })

  test('画面を閉じたら、知らせの登録を外す', async () => {
    vi.mocked(api.refresh).mockResolvedValue(auth())
    const { unmount } = renderProvider()
    await screen.findByText('yamada:false')

    unmount()

    expect(api.setUnauthorizedHandler).toHaveBeenLastCalledWith(null)
  })
})

test('AuthProvider の外で useAuth を使うとエラー', () => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  expect(() => render(<Probe />)).toThrow('useAuth は AuthProvider の中で使ってください')
})
