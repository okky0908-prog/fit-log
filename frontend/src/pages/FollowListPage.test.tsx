import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { UserListItem } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-42 フォロー中・フォロワー一覧（docs/screens.md）。行は「ユーザー一覧の行（共通）」

function user(values: Partial<UserListItem>): UserListItem {
  return {
    username: 'suzuki',
    displayName: '鈴木一郎',
    iconUrl: null,
    isPrivate: false,
    bio: null,
    followStatus: 'none',
    me: false,
    ...values,
  }
}

const SUZUKI = user({ bio: 'よろしくお願いします' })
const TANAKA = user({
  username: 'tanaka',
  displayName: '田中',
  isPrivate: true,
  followStatus: 'requested',
})
const KATO = user({ username: 'kato', displayName: '加藤', followStatus: 'following' })
const MYSELF = user({ username: ME.username, displayName: ME.displayName, me: true })

beforeEach(() => {
  vi.mocked(api.fetchFollowing).mockReset()
  vi.mocked(api.fetchFollowers).mockReset()
  vi.mocked(api.followUser).mockReset()
  vi.mocked(api.unfollowUser).mockReset()
  startAs(ME)
})

describe('表示', () => {
  test('一覧の行に、名前・ユーザー名・自己紹介と、自分から見た状態のボタンを出す。自分の行にはボタンがない', async () => {
    vi.mocked(api.fetchFollowing).mockResolvedValue({
      items: [SUZUKI, TANAKA, KATO, MYSELF],
      nextCursor: null,
      hasNext: false,
    })
    renderApp('/users/sato/following')

    const rows = await screen.findAllByRole('listitem')
    expect(api.fetchFollowing).toHaveBeenCalledWith('sato', undefined)
    expect(rows).toHaveLength(4)
    expect(within(rows[0]).getByRole('link', { name: '鈴木一郎' })).toHaveAttribute(
      'href',
      '/users/suzuki',
    )
    expect(within(rows[0]).getByText('@suzuki')).toBeInTheDocument()
    expect(within(rows[0]).getByText('よろしくお願いします')).toBeInTheDocument()
    expect(within(rows[0]).getByRole('button', { name: 'フォローする' })).toBeInTheDocument()
    expect(within(rows[1]).getByText('鍵アカウント')).toBeInTheDocument()
    expect(within(rows[1]).getByRole('button', { name: 'リクエスト中' })).toBeInTheDocument()
    expect(within(rows[2]).getByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
    expect(within(rows[3]).queryByRole('button')).not.toBeInTheDocument()
  })

  test('タブで切り替えると、履歴を増やさずにフォロワー一覧を出す', async () => {
    vi.mocked(api.fetchFollowing).mockResolvedValue({ items: [], nextCursor: null, hasNext: false })
    vi.mocked(api.fetchFollowers).mockResolvedValue({
      items: [SUZUKI],
      nextCursor: null,
      hasNext: false,
    })
    const { router } = renderApp('/users/sato/following')

    expect(
      await screen.findByRole('tab', { name: 'フォロー中', selected: true }),
    ).toBeInTheDocument()
    expect(await screen.findByText('まだ誰もフォローしていません')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('tab', { name: 'フォロワー' }))

    expect(await screen.findByRole('link', { name: '鈴木一郎' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'フォロワー', selected: true })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/users/sato/followers')
    expect(router.state.historyAction).toBe('REPLACE')
  })

  test('鍵アカウントで見る権限がなければ、説明を出す', async () => {
    vi.mocked(api.fetchFollowers).mockRejectedValue(
      new api.ApiError(
        403,
        'PRIVATE_ACCOUNT',
        'このアカウントは、承認されたフォロワーだけが見られます',
      ),
    )
    renderApp('/users/sato/followers')

    expect(
      await screen.findByText(
        'このアカウントのフォロー・フォロワーは、承認されたフォロワーだけが見られます',
      ),
    ).toBeInTheDocument()
  })

  test('存在しないユーザーは「このアカウントは存在しません」', async () => {
    vi.mocked(api.fetchFollowers).mockRejectedValue(
      new api.ApiError(404, 'USER_NOT_FOUND', 'ユーザーが見つかりません'),
    )
    renderApp('/users/nobody/followers')

    expect(await screen.findByText('このアカウントは存在しません')).toBeInTheDocument()
  })

  test('続きがあれば「さらに表示」で読み込む', async () => {
    vi.mocked(api.fetchFollowers)
      .mockResolvedValueOnce({ items: [SUZUKI], nextCursor: 'next', hasNext: true })
      .mockResolvedValueOnce({ items: [KATO], nextCursor: null, hasNext: false })
    renderApp('/users/sato/followers')

    await userEvent.click(await screen.findByRole('button', { name: 'さらに表示' }))

    expect(await screen.findByRole('link', { name: '加藤' })).toBeInTheDocument()
    expect(api.fetchFollowers).toHaveBeenLastCalledWith('sato', 'next')
    expect(screen.queryByRole('button', { name: 'さらに表示' })).not.toBeInTheDocument()
  })

  test('← で戻る先がなければ、その人のプロフィールへ移る', async () => {
    vi.mocked(api.fetchFollowers).mockResolvedValue({ items: [], nextCursor: null, hasNext: false })
    vi.mocked(api.fetchProfile).mockReturnValue(new Promise(() => {}))
    const { router } = renderApp('/users/sato/followers')

    await userEvent.click(await screen.findByRole('button', { name: '@sato（戻る）' }))

    expect(router.state.location.pathname).toBe('/users/sato')
  })
})

test('行のボタンでフォローすると、その行のボタンが変わる', async () => {
  vi.mocked(api.fetchFollowers)
    .mockResolvedValueOnce({ items: [SUZUKI], nextCursor: null, hasNext: false })
    .mockResolvedValue({
      items: [{ ...SUZUKI, followStatus: 'following' }],
      nextCursor: null,
      hasNext: false,
    })
  vi.mocked(api.followUser).mockResolvedValue({ followStatus: 'following' })
  renderApp('/users/sato/followers')

  await userEvent.click(await screen.findByRole('button', { name: 'フォローする' }))

  expect(api.followUser).toHaveBeenCalledWith('suzuki')
  expect(await screen.findByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
})
