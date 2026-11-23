import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { NotificationItem } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-30 通知一覧（docs/screens.md）。フォローのリクエストの「承認」「拒否」は 11 で足す

const NOW = new Date('2026-11-23T10:00:00+09:00')
const SATO = { username: 'sato', displayName: '佐藤花子', iconUrl: null, isPrivate: false }

function item(values: Partial<NotificationItem>): NotificationItem {
  return {
    id: 1,
    type: 'follow',
    createdAt: '2026-11-23T09:57:00+09:00',
    read: false,
    actor: SATO,
    workout: null,
    comment: null,
    goal: null,
    ...values,
  }
}

const LIKE = item({
  id: 3,
  type: 'like',
  workout: { id: 12, bodyExcerpt: '今日は脚の日！' },
})
const FOLLOW = item({ id: 2, type: 'follow', createdAt: '2026-11-23T07:00:00+09:00' })
const OLD_READ = item({ id: 1, type: 'follow', read: true, createdAt: '2026-11-21T08:00:00+09:00' })

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  vi.mocked(api.fetchNotifications).mockReset()
  vi.mocked(api.markNotificationsRead).mockReset()
  vi.mocked(api.markNotificationsRead).mockResolvedValue(undefined)
  startAs(ME)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('表示', () => {
  test('種類ごとの文言・投稿の抜き出し・時刻を新しい順に出し、未読に印を付ける', async () => {
    vi.mocked(api.fetchNotifications).mockResolvedValue({
      items: [LIKE, FOLLOW, OLD_READ],
      nextCursor: null,
      hasNext: false,
    })

    renderApp('/notifications')

    expect(await screen.findByRole('heading', { level: 1, name: '通知' })).toBeInTheDocument()
    const rows = await screen.findAllByRole('listitem')
    expect(rows).toHaveLength(3)
    expect(rows[0]).toHaveTextContent(
      '佐藤花子さんがあなたの記録にナイストレーニングしました「今日は脚の日！」· 3分前',
    )
    expect(rows[1]).toHaveTextContent('佐藤花子さんがあなたをフォローしました· 3時間前')
    expect(rows[2]).toHaveTextContent('11/21')
    expect(within(rows[0]).getByText('未読')).toBeInTheDocument()
    expect(within(rows[2]).queryByText('未読')).not.toBeInTheDocument()
  })

  test('操作した人のアイコンがあれば画像を、なければ表示名の頭文字を出す', async () => {
    vi.mocked(api.fetchNotifications).mockResolvedValue({
      items: [{ ...LIKE, actor: { ...SATO, iconUrl: 'https://example.com/icon.png' } }, FOLLOW],
      nextCursor: null,
      hasNext: false,
    })

    renderApp('/notifications')

    const avatars = await within(await screen.findByRole('main')).findAllByTestId('avatar')
    expect(avatars[0]).toHaveAttribute('src', 'https://example.com/icon.png')
    expect(avatars[1]).toHaveTextContent('佐')
  })

  test('押すと対象の画面へ移る', async () => {
    vi.mocked(api.fetchNotifications).mockResolvedValue({
      items: [LIKE, FOLLOW],
      nextCursor: null,
      hasNext: false,
    })

    renderApp('/notifications')

    expect(await screen.findByRole('link', { name: /ナイストレーニング/ })).toHaveAttribute(
      'href',
      '/workouts/12',
    )
    expect(screen.getByRole('link', { name: /フォローしました/ })).toHaveAttribute(
      'href',
      '/users/sato',
    )
  })

  test('0件なら「通知はまだありません」と出し、既読にする通信はしない', async () => {
    vi.mocked(api.fetchNotifications).mockResolvedValue({
      items: [],
      nextCursor: null,
      hasNext: false,
    })

    renderApp('/notifications')

    expect(await screen.findByText('通知はまだありません')).toBeInTheDocument()
    expect(api.markNotificationsRead).not.toHaveBeenCalled()
  })

  test('取れなかったら理由を出し、「もう一度読み込む」で取り直す', async () => {
    vi.mocked(api.fetchNotifications)
      .mockRejectedValueOnce(new api.ApiError(400, 'VALIDATION_FAILED', '入力内容に誤りがあります'))
      .mockResolvedValueOnce({ items: [FOLLOW], nextCursor: null, hasNext: false })

    renderApp('/notifications')

    expect(await screen.findByText('入力内容に誤りがあります')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(await screen.findByText(/フォローしました/)).toBeInTheDocument()
  })
})

describe('既読', () => {
  test('開いたら、表示した未読の通知だけを既読にし、未読の数を取り直す。印は画面を離れるまで残す', async () => {
    vi.mocked(api.fetchNotifications).mockResolvedValue({
      items: [LIKE, FOLLOW, OLD_READ],
      nextCursor: null,
      hasNext: false,
    })
    vi.mocked(api.fetchUnreadCount).mockClear()

    renderApp('/notifications')

    await waitFor(() => expect(api.markNotificationsRead).toHaveBeenCalledWith([3, 2]))
    await waitFor(() => expect(api.fetchUnreadCount).toHaveBeenCalledTimes(2))
    expect(api.markNotificationsRead).toHaveBeenCalledOnce()
    expect(screen.getAllByText('未読')).toHaveLength(2)
  })

  test('既読にする通信に失敗しても、一覧は出たまま', async () => {
    vi.mocked(api.fetchNotifications).mockResolvedValue({
      items: [FOLLOW],
      nextCursor: null,
      hasNext: false,
    })
    vi.mocked(api.markNotificationsRead).mockRejectedValue(
      new api.ApiError(0, 'NETWORK_ERROR', api.NETWORK_ERROR_MESSAGE),
    )

    renderApp('/notifications')

    await waitFor(() => expect(api.markNotificationsRead).toHaveBeenCalled())
    expect(screen.getByText(/フォローしました/)).toBeInTheDocument()
  })
})

test('続きは「さらに表示」で読み込み、続きの未読も既読にする', async () => {
  vi.mocked(api.fetchNotifications)
    .mockResolvedValueOnce({ items: [LIKE], nextCursor: 'next-1', hasNext: true })
    .mockResolvedValueOnce({ items: [FOLLOW, OLD_READ], nextCursor: null, hasNext: false })
  renderApp('/notifications')
  await waitFor(() => expect(api.markNotificationsRead).toHaveBeenCalledWith([3]))

  await userEvent.click(await screen.findByRole('button', { name: 'さらに表示' }))

  expect(await screen.findAllByText(/フォローしました/)).toHaveLength(2)
  expect(api.fetchNotifications).toHaveBeenLastCalledWith('next-1')
  await waitFor(() => expect(api.markNotificationsRead).toHaveBeenLastCalledWith([2]))
  expect(screen.queryByRole('button', { name: 'さらに表示' })).not.toBeInTheDocument()
})
