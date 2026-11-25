import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { Profile, WorkoutCard } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-40 プロフィール（docs/screens.md）。公開している目標は段階 3 で足す

function profile(values: Partial<Profile>): Profile {
  return {
    username: 'sato',
    displayName: '佐藤花子',
    iconUrl: null,
    isPrivate: false,
    bio: '週4で筋トレしています',
    followingCount: 12,
    followerCount: 8,
    me: false,
    followStatus: 'none',
    canViewRecords: true,
    ...values,
  }
}

const SATO = profile({})
const MY_PROFILE = profile({
  username: ME.username,
  displayName: ME.displayName,
  bio: null,
  me: true,
})

beforeEach(() => {
  vi.mocked(api.fetchProfile).mockReset()
  vi.mocked(api.followUser).mockReset()
  vi.mocked(api.unfollowUser).mockReset()
  vi.mocked(api.fetchUserWorkouts).mockReset()
  vi.mocked(api.likeWorkout).mockReset()
  startAs(ME)
})

describe('表示', () => {
  test('アイコン・表示名・ユーザー名・自己紹介・数を出し、数を押すとフォロー一覧へ', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(SATO)
    const { router } = renderApp('/users/sato')

    expect(await screen.findByRole('heading', { level: 1, name: '佐藤花子' })).toBeInTheDocument()
    expect(screen.getByText('@sato')).toBeInTheDocument()
    expect(screen.getByText('週4で筋トレしています')).toBeInTheDocument()
    // アイコンがなければ表示名の頭文字
    expect(within(screen.getByRole('main')).getByTestId('avatar')).toHaveTextContent('佐')
    expect(api.fetchProfile).toHaveBeenCalledWith('sato')

    await userEvent.click(screen.getByRole('link', { name: '12 フォロー中' }))
    expect(router.state.location.pathname).toBe('/users/sato/following')
  })

  test('アイコンがあれば画像を出す', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(
      profile({ iconUrl: 'https://example.com/icon.png' }),
    )
    renderApp('/users/sato')

    expect(await within(await screen.findByRole('main')).findByTestId('avatar')).toHaveAttribute(
      'src',
      'https://example.com/icon.png',
    )
  })

  test('鍵アカウントで見る権限がなければ、記録の代わりに説明を出し、数は押せない', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(
      profile({ isPrivate: true, canViewRecords: false }),
    )
    renderApp('/users/sato')

    expect(
      await screen.findByText('このアカウントの記録は、承認されたフォロワーだけが見られます'),
    ).toBeInTheDocument()
    expect(screen.getByText('鍵アカウント')).toBeInTheDocument()
    expect(screen.getByText('8 フォロワー')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '8 フォロワー' })).not.toBeInTheDocument()
    expect(screen.getByText('12 フォロー中')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '12 フォロー中' })).not.toBeInTheDocument()
  })

  test('自分のプロフィールには、フォローボタンの代わりに「プロフィールを編集」を出す', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(MY_PROFILE)
    renderApp('/users/yamada')

    expect(await screen.findByRole('link', { name: 'プロフィールを編集' })).toHaveAttribute(
      'href',
      '/settings/profile',
    )
    expect(screen.queryByRole('button', { name: /フォロー/ })).not.toBeInTheDocument()
  })

  test('存在しないユーザーは「このアカウントは存在しません」', async () => {
    vi.mocked(api.fetchProfile).mockRejectedValue(
      new api.ApiError(404, 'USER_NOT_FOUND', 'ユーザーが見つかりません'),
    )
    renderApp('/users/nobody')

    expect(await screen.findByText('このアカウントは存在しません')).toBeInTheDocument()
  })

  test('読み込めなければ理由と「もう一度読み込む」を出す', async () => {
    // 通信の失敗・5xx は自動でやり直すので、やり直さない 4xx で確かめる
    vi.mocked(api.fetchProfile)
      .mockRejectedValueOnce(new api.ApiError(400, 'MALFORMED_REQUEST', '読み込めませんでした'))
      .mockResolvedValueOnce(SATO)
    renderApp('/users/sato')

    expect(await screen.findByText('読み込めませんでした')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(await screen.findByRole('heading', { level: 1, name: '佐藤花子' })).toBeInTheDocument()
  })
})

describe('フォローボタン', () => {
  test('フォローすると、ボタンと数が変わる', async () => {
    vi.mocked(api.fetchProfile)
      .mockResolvedValueOnce(SATO)
      .mockResolvedValue(profile({ followStatus: 'following', followerCount: 9 }))
    vi.mocked(api.followUser).mockResolvedValue({ followStatus: 'following' })
    renderApp('/users/sato')

    await userEvent.click(await screen.findByRole('button', { name: 'フォローする' }))

    expect(api.followUser).toHaveBeenCalledWith('sato')
    expect(await screen.findByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
    expect(await screen.findByText('9 フォロワー')).toBeInTheDocument()
  })

  test('鍵アカウントには「フォローをリクエスト」と出し、押すとリクエスト中になる', async () => {
    const locked = profile({ isPrivate: true, canViewRecords: false })
    vi.mocked(api.fetchProfile)
      .mockResolvedValueOnce(locked)
      .mockResolvedValue({ ...locked, followStatus: 'requested' })
    vi.mocked(api.followUser).mockResolvedValue({ followStatus: 'requested' })
    renderApp('/users/sato')

    await userEvent.click(await screen.findByRole('button', { name: 'フォローをリクエスト' }))

    expect(await screen.findByRole('button', { name: 'リクエスト中' })).toBeInTheDocument()
  })

  test('リクエスト中を押すと確認してから取り消す', async () => {
    const requested = profile({ isPrivate: true, canViewRecords: false, followStatus: 'requested' })
    vi.mocked(api.fetchProfile)
      .mockResolvedValueOnce(requested)
      .mockResolvedValue({ ...requested, followStatus: 'none' })
    vi.mocked(api.unfollowUser).mockResolvedValue({ followStatus: 'none' })
    renderApp('/users/sato')

    await userEvent.click(await screen.findByRole('button', { name: 'リクエスト中' }))
    const dialog = screen.getByRole('dialog', { name: 'リクエストを取り消しますか？' })
    await userEvent.click(within(dialog).getByRole('button', { name: '取り消す' }))

    expect(api.unfollowUser).toHaveBeenCalledWith('sato')
    expect(await screen.findByRole('button', { name: 'フォローをリクエスト' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('フォロー中を押すと解除を確認し、キャンセルなら何もしない', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(profile({ followStatus: 'following' }))
    renderApp('/users/sato')

    await userEvent.click(await screen.findByRole('button', { name: 'フォロー中' }))
    const dialog = screen.getByRole('dialog', { name: 'フォローを解除しますか？' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'キャンセル' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.unfollowUser).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'フォロー中' })).toBeInTheDocument()
  })

  test('送信中はボタンを押せない（連打で2回送らない）', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(SATO)
    vi.mocked(api.followUser).mockReturnValue(new Promise(() => {}))
    renderApp('/users/sato')

    const button = await screen.findByRole('button', { name: 'フォローする' })
    await userEvent.click(button)

    expect(button).toBeDisabled()
    await userEvent.click(button)
    expect(api.followUser).toHaveBeenCalledTimes(1)
  })

  test('失敗したら、お知らせで理由を出し、ボタンは元のまま', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(SATO)
    vi.mocked(api.followUser).mockRejectedValue(
      new api.ApiError(0, 'NETWORK_ERROR', api.NETWORK_ERROR_MESSAGE),
    )
    renderApp('/users/sato')

    await userEvent.click(await screen.findByRole('button', { name: 'フォローする' }))

    expect(await screen.findByText(api.NETWORK_ERROR_MESSAGE)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: 'フォローする' })).toBeEnabled())
  })
})

describe('記録の一覧', () => {
  function card(id: number, body: string): WorkoutCard {
    return {
      id,
      author: { username: 'sato', displayName: '佐藤花子', iconUrl: null, isPrivate: false },
      performedOn: '2026-11-14',
      createdAt: '2026-11-14T19:00:00+09:00',
      editedAt: null,
      body,
      visibility: 'public',
      exerciseSummaries: [],
      otherExerciseCount: 0,
      images: [],
      likeCount: 0,
      likedByMe: false,
      commentCount: 0,
      mine: false,
    }
  }

  test('その人の記録を投稿カードで出し、続きを読み込める', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(SATO)
    vi.mocked(api.fetchUserWorkouts)
      .mockResolvedValueOnce({ items: [card(1, '脚の日')], nextCursor: 'next', hasNext: true })
      .mockResolvedValueOnce({ items: [card(2, '胸の日')], nextCursor: null, hasNext: false })
    renderApp('/users/sato')

    const records = await screen.findByRole('region', { name: '記録' })
    expect(await within(records).findByRole('article')).toHaveTextContent('脚の日')
    expect(api.fetchUserWorkouts).toHaveBeenCalledWith('sato', undefined)

    await userEvent.click(within(records).getByRole('button', { name: 'さらに表示' }))
    expect(await within(records).findByText('胸の日')).toBeInTheDocument()
    expect(api.fetchUserWorkouts).toHaveBeenLastCalledWith('sato', 'next')
  })

  test('記録のナイストレーニングを押すと、すぐ付けた状態になる', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(SATO)
    vi.mocked(api.fetchUserWorkouts).mockResolvedValue({
      items: [card(1, '脚の日')],
      nextCursor: null,
      hasNext: false,
    })
    vi.mocked(api.likeWorkout).mockReturnValue(new Promise(() => {}))
    renderApp('/users/sato')

    const button = await screen.findByRole('button', { name: /ナイストレーニング/ })
    await userEvent.click(button)

    expect(api.likeWorkout).toHaveBeenCalledWith(1)
    expect(button).toHaveAttribute('aria-pressed', 'true')
    expect(button).toHaveTextContent(/1$/)
  })

  test('記録がなければ「まだ記録がありません」', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(SATO)
    vi.mocked(api.fetchUserWorkouts).mockResolvedValue({
      items: [],
      nextCursor: null,
      hasNext: false,
    })
    renderApp('/users/sato')

    expect(await screen.findByText('まだ記録がありません')).toBeInTheDocument()
  })

  test('見る権限がなければ、記録の一覧を読み込まない', async () => {
    vi.mocked(api.fetchProfile).mockResolvedValue(
      profile({ isPrivate: true, canViewRecords: false }),
    )
    renderApp('/users/sato')

    expect(
      await screen.findByText('このアカウントの記録は、承認されたフォロワーだけが見られます'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '記録' })).not.toBeInTheDocument()
    expect(api.fetchUserWorkouts).not.toHaveBeenCalled()
  })
})
