import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { Comment, Reply, Workout } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-12 投稿詳細（docs/screens.md）。表示・単位・見られない記録・自分の記録の削除。
// 消費カロリー（段階 5）は、その段階で足す

const NOW = new Date('2026-11-22T10:00:00+09:00')

const WORKOUT: Workout = {
  id: 12,
  author: { username: 'sato', displayName: '佐藤花子', iconUrl: null, isPrivate: false },
  performedOn: '2026-11-20',
  createdAt: '2026-11-22T09:57:00+09:00',
  editedAt: null,
  body: '今日は脚の日！\n#脚トレ',
  durationMin: 75,
  visibility: 'public',
  exercises: [
    {
      exerciseId: 16,
      name: 'スクワット',
      bodyPart: 'legs',
      recordType: 'weight_reps',
      isRunning: false,
      personalBest: false,
      sets: [
        { weightKg: 60, reps: 10, durationSec: null, distanceKm: null, personalBest: false },
        { weightKg: 100, reps: 5, durationSec: null, distanceKm: null, personalBest: false },
      ],
    },
    {
      exerciseId: 48,
      name: 'ランニング',
      bodyPart: 'cardio',
      recordType: 'time_distance',
      isRunning: true,
      personalBest: false,
      sets: [{ weightKg: null, reps: null, durationSec: 1800, distanceKm: 5, personalBest: false }],
    },
  ],
  images: [
    { id: 1, url: 'https://example.com/a.jpg', sortOrder: 1 },
    { id: 2, url: 'https://example.com/b.jpg', sortOrder: 2 },
  ],
  likeCount: 0,
  likedByMe: false,
  commentCount: 0,
  mine: false,
}

const MINE: Workout = {
  ...WORKOUT,
  author: { ...WORKOUT.author, username: ME.username },
  mine: true,
}

beforeEach(() => {
  // 日時だけを止める（通信の待ちなど、ほかのタイマーは本物のまま）
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  vi.mocked(api.fetchWorkout).mockReset()
  vi.mocked(api.deleteWorkout).mockReset()
  vi.mocked(api.likeWorkout).mockReset()
  vi.mocked(api.fetchComments).mockReset()
  vi.mocked(api.fetchComments).mockResolvedValue({ items: [], nextCursor: null, hasNext: false })
  vi.mocked(api.postComment).mockReset()
  vi.mocked(api.deleteComment).mockReset()
  vi.mocked(api.fetchReplies).mockReset()
  startAs(ME)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('表示', () => {
  test('投稿者・実施日・投稿日時・公開範囲・ひとこと・時間・種目とセット・画像を出す', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)

    renderApp('/workouts/12')

    expect(await screen.findByText('佐藤花子')).toBeInTheDocument()
    expect(api.fetchWorkout).toHaveBeenCalledWith(12)
    expect(screen.getByText('@sato')).toBeInTheDocument()
    expect(screen.getByText('11/20（金）')).toBeInTheDocument()
    expect(screen.getByText('3分前に投稿')).toBeInTheDocument()
    expect(screen.getByText('公開')).toBeInTheDocument()
    expect(screen.queryByText('編集済み')).not.toBeInTheDocument()
    // 改行は残す（表示は CSS の white-space: pre-wrap）
    expect(screen.getByText(/今日は脚の日！/)).toHaveTextContent('今日は脚の日！ #脚トレ')
    expect(screen.getByText('トレーニング時間 75分')).toBeInTheDocument()

    const squat = screen.getByRole('region', { name: 'スクワット（脚）' })
    const squatRows = within(squat).getAllByRole('listitem')
    expect(squatRows.map((row) => row.textContent)).toEqual(['160kg × 10回', '2100kg × 5回'])
    const running = screen.getByRole('region', { name: 'ランニング（有酸素）' })
    expect(within(running).getByRole('listitem')).toHaveTextContent('5.00km30:006:00/km')

    expect(
      screen.getAllByRole('img', { name: /^画像/ }).map((img) => img.getAttribute('src')),
    ).toEqual(['https://example.com/a.jpg', 'https://example.com/b.jpg'])
    expect(screen.getByAltText('画像 1 / 2')).toBeInTheDocument()
  })

  test('ユーザーが選んだ単位（lb・マイル）で出す', async () => {
    startAs({ ...ME, weightUnit: 'lb', distanceUnit: 'mi' })
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)

    renderApp('/workouts/12')

    const squat = await screen.findByRole('region', { name: 'スクワット（脚）' })
    expect(within(squat).getAllByRole('listitem')[0]).toHaveTextContent('132.5lb × 10回')
    expect(screen.getByRole('region', { name: 'ランニング（有酸素）' })).toHaveTextContent('3.11mi')
  })

  test('編集済み・鍵アカウント・フォロワー限定・自己ベスト更新の印を出し、ないものは出さない', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue({
      ...WORKOUT,
      author: { ...WORKOUT.author, isPrivate: true },
      createdAt: '2026-11-20T21:00:00+09:00',
      editedAt: '2026-11-21T08:00:00+09:00',
      visibility: 'followers',
      body: '',
      durationMin: null,
      images: [],
      exercises: [
        {
          ...WORKOUT.exercises[0],
          personalBest: true,
          sets: [
            { ...WORKOUT.exercises[0].sets[0] },
            { ...WORKOUT.exercises[0].sets[1], personalBest: true },
          ],
        },
      ],
    })

    renderApp('/workouts/12')

    expect(await screen.findByText('編集済み')).toBeInTheDocument()
    expect(screen.getByText('鍵アカウント')).toBeInTheDocument()
    expect(screen.getByText('フォロワー限定')).toBeInTheDocument()
    // 実施日と同じ日の投稿なので、投稿日時は出さない
    expect(screen.queryByText(/前に投稿/)).not.toBeInTheDocument()
    expect(screen.queryByText(/トレーニング時間/)).not.toBeInTheDocument()
    expect(screen.queryByRole('img', { name: /^画像/ })).not.toBeInTheDocument()
    const squat = screen.getByRole('region', { name: 'スクワット（脚）' })
    expect(within(squat).getByText('🏆自己ベスト更新')).toBeInTheDocument()
    const rows = within(squat).getAllByRole('listitem')
    expect(within(rows[0]).queryByText('自己ベスト')).not.toBeInTheDocument()
    expect(within(rows[1]).getByText('自己ベスト')).toBeInTheDocument()
  })

  test('自分のみの記録は、鍵のアイコンで出す', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue({ ...MINE, visibility: 'private' })

    renderApp('/workouts/12')

    expect(await screen.findByText('自分のみ')).toBeInTheDocument()
  })
})

test('コメント数と、ナイストレーニングのボタンを出し、押すと付ける', async () => {
  vi.mocked(api.fetchWorkout).mockResolvedValue({ ...WORKOUT, commentCount: 3, likeCount: 12 })
  vi.mocked(api.likeWorkout).mockResolvedValue({ likeCount: 13, likedByMe: true })
  renderApp('/workouts/12')

  const article = await screen.findByRole('article')
  expect(within(article).getByTitle('コメント')).toHaveTextContent('3')
  const button = within(article).getByRole('button', { name: /ナイストレーニング/ })
  expect(button).toHaveAttribute('aria-pressed', 'false')

  await userEvent.click(button)

  expect(api.likeWorkout).toHaveBeenCalledWith(12)
  await waitFor(() => expect(button).toHaveTextContent(/13$/))
  expect(button).toHaveAttribute('aria-pressed', 'true')
})

test('投稿者のアイコン・表示名・ユーザー名は、押すとその人のプロフィールへ移る', async () => {
  vi.mocked(api.fetchWorkout).mockResolvedValue({
    ...WORKOUT,
    author: { ...WORKOUT.author, iconUrl: 'https://example.com/icon.png' },
  })
  vi.mocked(api.fetchProfile).mockReturnValue(new Promise(() => {}))
  const { router } = renderApp('/workouts/12')

  expect(await within(await screen.findByRole('main')).findByTestId('avatar')).toHaveAttribute(
    'src',
    'https://example.com/icon.png',
  )
  const username = WORKOUT.author.username
  for (const name of [
    `${WORKOUT.author.displayName}のプロフィール`,
    WORKOUT.author.displayName,
    `@${username}`,
  ]) {
    expect(screen.getByRole('link', { name })).toHaveAttribute('href', `/users/${username}`)
  }
  await userEvent.click(screen.getByRole('link', { name: WORKOUT.author.displayName }))
  expect(router.state.location.pathname).toBe(`/users/${username}`)
})

describe('見られない記録・通信の失敗', () => {
  test('見てはいけない・ない記録は「この記録は見つかりません」', async () => {
    vi.mocked(api.fetchWorkout).mockRejectedValue(
      new api.ApiError(404, 'WORKOUT_NOT_FOUND', '記録が見つかりません'),
    )

    renderApp('/workouts/12')

    expect(await screen.findByText('この記録は見つかりません')).toBeInTheDocument()
  })

  test('ID が数でなければ、API を呼ばずに「この記録は見つかりません」', async () => {
    renderApp('/workouts/abc')

    expect(await screen.findByText('この記録は見つかりません')).toBeInTheDocument()
    expect(api.fetchWorkout).not.toHaveBeenCalled()
  })

  test(
    '通信に失敗したら（自動のやり直しも失敗したら）文言を出し、「もう一度読み込む」で取り直す',
    { timeout: 10_000 },
    async () => {
      const failure = new api.ApiError(0, 'NETWORK_ERROR', api.NETWORK_ERROR_MESSAGE)
      // 通信の失敗は、TanStack Query が2回までやり直す（lib/queryClient.ts）。3回とも失敗させる
      vi.mocked(api.fetchWorkout)
        .mockRejectedValueOnce(failure)
        .mockRejectedValueOnce(failure)
        .mockRejectedValueOnce(failure)
        .mockResolvedValueOnce(WORKOUT)

      renderApp('/workouts/12')

      expect(
        await screen.findByText(api.NETWORK_ERROR_MESSAGE, {}, { timeout: 5_000 }),
      ).toBeInTheDocument()
      expect(api.fetchWorkout).toHaveBeenCalledTimes(3)
      await userEvent.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
      expect(await screen.findByText('佐藤花子')).toBeInTheDocument()
    },
  )
})

test('自分の記録の … メニューの「編集」で、編集の画面へ移る', async () => {
  vi.mocked(api.fetchWorkout).mockResolvedValue(MINE)
  vi.mocked(api.fetchRecentExercises).mockResolvedValue([])
  const { router } = renderApp('/workouts/12')

  await userEvent.click(await screen.findByRole('button', { name: 'メニュー' }))
  await userEvent.click(screen.getByRole('menuitem', { name: '編集' }))

  expect(router.state.location.pathname).toBe('/workouts/12/edit')
})

describe('自分の記録の削除', () => {
  test('他人の記録には … メニューを出さない', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)

    renderApp('/workouts/12')

    await screen.findByText('佐藤花子')
    expect(screen.queryByRole('button', { name: 'メニュー' })).not.toBeInTheDocument()
  })

  test('削除は確認してから行い、お知らせを出してホームへ移る', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(MINE)
    vi.mocked(api.deleteWorkout).mockResolvedValue(undefined)
    renderApp('/workouts/12')

    await userEvent.click(await screen.findByRole('button', { name: 'メニュー' }))
    await userEvent.click(screen.getByRole('menuitem', { name: '削除' }))
    const dialog = screen.getByRole('dialog', { name: 'この記録を削除しますか？' })
    expect(api.deleteWorkout).not.toHaveBeenCalled()
    await userEvent.click(within(dialog).getByRole('button', { name: '削除する' }))

    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
    expect(api.deleteWorkout).toHaveBeenCalledWith(12)
    expect(screen.getByText('記録を削除しました')).toBeInTheDocument()
  })

  test('キャンセル・Esc では削除しない', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(MINE)
    renderApp('/workouts/12')

    await userEvent.click(await screen.findByRole('button', { name: 'メニュー' }))
    await userEvent.click(screen.getByRole('menuitem', { name: '削除' }))
    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    await userEvent.click(screen.getByRole('menuitem', { name: '削除' }))
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.deleteWorkout).not.toHaveBeenCalled()
  })

  test('削除に失敗したら、お知らせで理由を出して画面に残る', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(MINE)
    vi.mocked(api.deleteWorkout).mockRejectedValue(
      new api.ApiError(0, 'NETWORK_ERROR', api.NETWORK_ERROR_MESSAGE),
    )
    renderApp('/workouts/12')

    await userEvent.click(await screen.findByRole('button', { name: 'メニュー' }))
    await userEvent.click(screen.getByRole('menuitem', { name: '削除' }))
    await userEvent.click(screen.getByRole('button', { name: '削除する' }))

    expect(await screen.findByText(api.NETWORK_ERROR_MESSAGE)).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByText('佐藤花子')).toBeInTheDocument()
  })
})

test('← は直前の画面に戻り、戻る先がなければホームへ移る', async () => {
  vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
  // 戻る先がない（URL を直接開いた）
  renderApp('/workouts/12')

  await userEvent.click(await screen.findByRole('button', { name: '戻る' }))

  expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
})

test('戻る先があれば、ホームではなく直前の画面に戻る', async () => {
  vi.mocked(api.fetchWorkout).mockImplementation(async (id) => ({
    ...WORKOUT,
    id,
    body: `記録${id}`,
  }))
  const { router } = renderApp('/workouts/7')
  expect(await screen.findByText('記録7')).toBeInTheDocument()
  await router.navigate('/workouts/12')
  expect(await screen.findByText('記録12')).toBeInTheDocument()

  await userEvent.click(screen.getByRole('button', { name: '戻る' }))

  expect(await screen.findByText('記録7')).toBeInTheDocument()
})

test('確認ダイアログは、外側を押しても閉じる', async () => {
  vi.mocked(api.fetchWorkout).mockResolvedValue(MINE)
  renderApp('/workouts/12')

  await userEvent.click(await screen.findByRole('button', { name: 'メニュー' }))
  await userEvent.click(screen.getByRole('menuitem', { name: '削除' }))
  // 開いたら「キャンセル」に移る（うっかり Enter で削除しないように）
  expect(screen.getByRole('button', { name: 'キャンセル' })).toHaveFocus()
  await userEvent.click(screen.getByRole('button', { name: 'ダイアログを閉じる' }))

  expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  expect(api.deleteWorkout).not.toHaveBeenCalled()
})

describe('画像の拡大表示', () => {
  test('押すと拡大し、‹ › と左右の矢印キーで移り、端では止まる', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
    renderApp('/workouts/12')

    await userEvent.click(await screen.findByRole('button', { name: '画像 1 / 2 を拡大' }))

    const dialog = screen.getByRole('dialog', { name: '画像 1 / 2' })
    expect(within(dialog).getByRole('img')).toHaveAttribute('src', 'https://example.com/a.jpg')
    expect(within(dialog).getByRole('button', { name: '前の画像' })).toBeDisabled()
    await userEvent.click(within(dialog).getByRole('button', { name: '次の画像' }))
    expect(screen.getByRole('dialog', { name: '画像 2 / 2' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '次の画像' })).toBeDisabled()
    await userEvent.keyboard('{ArrowRight}')
    expect(screen.getByRole('dialog', { name: '画像 2 / 2' })).toBeInTheDocument()
    await userEvent.keyboard('{ArrowLeft}')
    expect(screen.getByRole('dialog', { name: '画像 1 / 2' })).toBeInTheDocument()
  })

  test('Esc と外側のクリックで閉じる', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
    renderApp('/workouts/12')

    await userEvent.click(await screen.findByRole('button', { name: '画像 2 / 2 を拡大' }))
    expect(screen.getByRole('dialog', { name: '画像 2 / 2' })).toBeInTheDocument()
    await userEvent.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '画像 1 / 2 を拡大' }))
    await userEvent.click(screen.getByRole('button', { name: '拡大表示を閉じる' }))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('1枚だけなら、‹ › を出さない', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue({ ...WORKOUT, images: [WORKOUT.images[0]] })
    renderApp('/workouts/12')

    await userEvent.click(await screen.findByRole('button', { name: '画像 1 / 1 を拡大' }))

    expect(screen.getByRole('dialog', { name: '画像 1 / 1' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '次の画像' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '前の画像' })).not.toBeInTheDocument()
  })
})

describe('コメント欄', () => {
  const SATO = { username: 'sato', displayName: '佐藤花子', iconUrl: null, isPrivate: false }
  const YAMADA = { username: 'yamada', displayName: '山田太郎', iconUrl: null, isPrivate: false }

  function reply(id: number, values: Partial<Reply> = {}): Reply {
    return {
      id,
      author: YAMADA,
      replyToUser: SATO,
      body: `返信${id}`,
      createdAt: '2026-11-22T09:58:00+09:00',
      deletable: false,
      ...values,
    }
  }

  function comment(id: number, values: Partial<Comment> = {}): Comment {
    return {
      id,
      author: SATO,
      body: `コメント${id}`,
      createdAt: '2026-11-22T09:58:00+09:00',
      deletable: false,
      replies: [],
      replyCount: 0,
      repliesNextCursor: null,
      ...values,
    }
  }

  function comments(items: Comment[], nextCursor: string | null = null) {
    return { items, nextCursor, hasNext: nextCursor !== null }
  }

  const input = () => screen.getByRole('textbox', { name: 'コメントを入力' })

  test('コメントと返信を古い順に出し、返信先の @ユーザー名 を添える', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
    vi.mocked(api.fetchComments).mockResolvedValue(
      comments([
        comment(501, {
          body: 'フォームきれいでした！',
          replies: [reply(502, { body: 'ありがとうございます！' })],
          replyCount: 1,
        }),
        comment(503, { author: YAMADA, body: 'ナイスです' }),
      ]),
    )
    renderApp('/workouts/12')

    const section = await screen.findByRole('region', { name: 'コメント' })
    const items = await within(section).findAllByRole('article')
    expect(api.fetchComments).toHaveBeenCalledWith(12, undefined)
    expect(items.map((item) => item.textContent)).toEqual([
      expect.stringContaining('フォームきれいでした！'),
      expect.stringContaining('ありがとうございます！'),
      expect.stringContaining('ナイスです'),
    ])
    expect(items[1]).toHaveTextContent('@sato ありがとうございます！')
    expect(within(items[0]).getByRole('link', { name: '佐藤花子' })).toHaveAttribute(
      'href',
      '/users/sato',
    )
  })

  test('返信が4件以上なら「返信をさらに表示（N件）」で続きを読み込み、全部出したら消す', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
    vi.mocked(api.fetchComments).mockResolvedValue(
      comments([
        comment(501, {
          replies: [reply(1), reply(2), reply(3)],
          replyCount: 5,
          repliesNextCursor: 'r3',
        }),
      ]),
    )
    vi.mocked(api.fetchReplies).mockResolvedValue({
      items: [reply(4), reply(5)],
      nextCursor: null,
      hasNext: false,
    })
    renderApp('/workouts/12')

    await userEvent.click(await screen.findByRole('button', { name: '返信をさらに表示（2件）' }))

    expect(api.fetchReplies).toHaveBeenCalledWith(501, 'r3')
    expect(await screen.findByText('返信5')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /返信をさらに表示/ })).not.toBeInTheDocument()
  })

  test('返信が消されて数が合わず、まだ出していない返信がなければ「返信をさらに表示」を出さない', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
    vi.mocked(api.fetchComments).mockResolvedValue(
      comments([
        comment(501, {
          replies: [reply(1), reply(2), reply(3)],
          replyCount: 3,
          repliesNextCursor: 'r3',
        }),
      ]),
    )
    renderApp('/workouts/12')

    expect(await screen.findByText('返信3')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /返信をさらに表示/ })).not.toBeInTheDocument()
  })

  test('コメントの続きは「さらに表示」で読み込む', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
    vi.mocked(api.fetchComments)
      .mockResolvedValueOnce(comments([comment(501)], 'next'))
      .mockResolvedValueOnce(comments([comment(502)]))
    renderApp('/workouts/12')

    await userEvent.click(await screen.findByRole('button', { name: 'さらに表示' }))

    expect(await screen.findByText('コメント502')).toBeInTheDocument()
    expect(api.fetchComments).toHaveBeenLastCalledWith(12, 'next')
    expect(screen.queryByRole('button', { name: 'さらに表示' })).not.toBeInTheDocument()
  })

  describe('投稿', () => {
    test('文字数を数え、空や141文字ではボタンを押せず、超えたら数を赤字にする', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
      renderApp('/workouts/12')

      const button = await screen.findByRole('button', { name: 'コメントする' })
      expect(screen.getByText('0 / 140')).toBeInTheDocument()
      expect(button).toBeDisabled()

      await userEvent.type(input(), 'ナイス💪')
      expect(screen.getByText('4 / 140')).toBeInTheDocument()
      expect(button).toBeEnabled()

      await userEvent.clear(input())
      await userEvent.click(input())
      await userEvent.paste('あ'.repeat(141))
      expect(screen.getByText('141 / 140')).toHaveAttribute('data-over', 'true')
      expect(button).toBeDisabled()
    })

    test('投稿すると最後に足して入力欄を空にし、コメント数を合わせる', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue({ ...WORKOUT, commentCount: 1 })
      vi.mocked(api.fetchComments).mockResolvedValue(comments([comment(501)]))
      vi.mocked(api.postComment).mockResolvedValue({
        comment: comment(510, { author: YAMADA, body: 'ナイス', deletable: true }),
        commentCount: 2,
      })
      renderApp('/workouts/12')

      await userEvent.type(await screen.findByRole('textbox', { name: 'コメントを入力' }), 'ナイス')
      await userEvent.click(screen.getByRole('button', { name: 'コメントする' }))

      expect(api.postComment).toHaveBeenCalledWith(12, 'ナイス')
      const section = screen.getByRole('region', { name: 'コメント' })
      await waitFor(() => expect(within(section).getAllByRole('article')).toHaveLength(2))
      expect(within(section).getAllByRole('article')[1]).toHaveTextContent('ナイス')
      expect(input()).toHaveValue('')
      expect(screen.getByTitle('コメント')).toHaveTextContent('2')
    })

    test('投稿できなければ、お知らせで理由を出し、入力は残す', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
      vi.mocked(api.postComment).mockRejectedValue(
        new api.ApiError(404, 'WORKOUT_NOT_FOUND', '記録が見つかりません'),
      )
      renderApp('/workouts/12')

      await userEvent.type(await screen.findByRole('textbox', { name: 'コメントを入力' }), 'ナイス')
      await userEvent.click(screen.getByRole('button', { name: 'コメントする' }))

      expect(await screen.findByText('記録が見つかりません')).toBeInTheDocument()
      expect(input()).toHaveValue('ナイス')
    })

    test('自分のみの記録では、入力欄の見出しを「メモを書く」にする', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue({ ...MINE, visibility: 'private' })
      renderApp('/workouts/12')

      expect(await screen.findByRole('textbox', { name: 'メモを書く' })).toBeInTheDocument()
    })

    test('💬 を押すと、入力欄にカーソルを合わせる', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
      renderApp('/workouts/12')

      await userEvent.click(await screen.findByRole('button', { name: /コメント欄へ/ }))

      expect(input()).toHaveFocus()
    })
  })

  describe('返信', () => {
    test('コメントの「返信」で入力欄を出し、送ると、その返信の最後にすぐ足す', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
      vi.mocked(api.fetchComments).mockResolvedValue(comments([comment(501)]))
      vi.mocked(api.postComment).mockResolvedValue({
        comment: reply(520, { body: 'ありがとう' }),
        commentCount: 2,
      })
      renderApp('/workouts/12')

      await userEvent.click(await screen.findByRole('button', { name: '返信' }))
      const box = screen.getByRole('textbox', { name: '返信を入力' })
      expect(box).toHaveValue('')
      expect(box).toHaveFocus()
      await userEvent.type(box, 'ありがとう')
      await userEvent.click(screen.getByRole('button', { name: '返信する' }))

      expect(api.postComment).toHaveBeenCalledWith(12, 'ありがとう', 501)
      expect(await screen.findByText('ありがとう')).toBeInTheDocument()
      expect(screen.queryByRole('textbox', { name: '返信を入力' })).not.toBeInTheDocument()
      expect(screen.getByTitle('コメント')).toHaveTextContent('2')
    })

    test('返信への「返信」は、同じ返信元の下に、@ユーザー名 を入れた入力欄を出す', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
      vi.mocked(api.fetchComments).mockResolvedValue(
        comments([comment(501, { replies: [reply(502)], replyCount: 1 })]),
      )
      vi.mocked(api.postComment).mockResolvedValue({
        comment: reply(530, { author: SATO, replyToUser: YAMADA, body: '@yamada どうも' }),
        commentCount: 3,
      })
      renderApp('/workouts/12')

      const replyItem = (await screen.findByText('返信502')).closest('article') as HTMLElement
      await userEvent.click(within(replyItem).getByRole('button', { name: '返信' }))
      const box = screen.getByRole('textbox', { name: '返信を入力' })
      expect(box).toHaveValue('@yamada ')
      await userEvent.type(box, 'どうも')
      await userEvent.click(screen.getByRole('button', { name: '返信する' }))

      expect(api.postComment).toHaveBeenCalledWith(12, '@yamada どうも', 502)
    })

    test('続きを読み込んでも、投稿した返信を重ねて出さない', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
      vi.mocked(api.fetchComments).mockResolvedValue(
        comments([
          comment(501, {
            replies: [reply(1), reply(2), reply(3)],
            replyCount: 4,
            repliesNextCursor: 'r3',
          }),
        ]),
      )
      vi.mocked(api.postComment).mockResolvedValue({
        comment: reply(9, { body: '投稿した返信' }),
        commentCount: 6,
      })
      vi.mocked(api.fetchReplies).mockResolvedValue({
        items: [reply(4), reply(9, { body: '投稿した返信' })],
        nextCursor: null,
        hasNext: false,
      })
      renderApp('/workouts/12')

      await userEvent.click((await screen.findAllByRole('button', { name: '返信' }))[0])
      await userEvent.type(screen.getByRole('textbox', { name: '返信を入力' }), '投稿した返信')
      await userEvent.click(screen.getByRole('button', { name: '返信する' }))
      await screen.findByText('投稿した返信')
      await userEvent.click(screen.getByRole('button', { name: '返信をさらに表示（1件）' }))

      expect(await screen.findByText('返信4')).toBeInTheDocument()
      expect(screen.getAllByText('投稿した返信')).toHaveLength(1)
    })
  })

  describe('削除', () => {
    test('消せるものにだけ「削除」を出し、返信元は「返信も削除されます」と確かめてから消す', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue({ ...WORKOUT, commentCount: 3 })
      vi.mocked(api.fetchComments).mockResolvedValue(
        comments([
          comment(501, { deletable: true, replies: [reply(502)], replyCount: 1 }),
          comment(503),
        ]),
      )
      vi.mocked(api.deleteComment).mockResolvedValue({ commentCount: 1 })
      renderApp('/workouts/12')

      const section = await screen.findByRole('region', { name: 'コメント' })
      await within(section).findByText('コメント501')
      expect(within(section).getAllByRole('button', { name: '削除' })).toHaveLength(1)
      await userEvent.click(within(section).getByRole('button', { name: '削除' }))
      const dialog = screen.getByRole('dialog', { name: 'このコメントを削除しますか？' })
      expect(dialog).toHaveTextContent('返信も削除されます')
      await userEvent.click(within(dialog).getByRole('button', { name: '削除する' }))

      expect(api.deleteComment).toHaveBeenCalledWith(501)
      await waitFor(() => expect(screen.queryByText('コメント501')).not.toBeInTheDocument())
      expect(screen.queryByText('返信502')).not.toBeInTheDocument()
      expect(screen.getByText('コメント503')).toBeInTheDocument()
      expect(screen.getByTitle('コメント')).toHaveTextContent('1')
      expect(await screen.findByText('コメントを削除しました')).toBeInTheDocument()
    })

    test('返信の削除では「返信も削除されます」を出さない', async () => {
      vi.mocked(api.fetchWorkout).mockResolvedValue(WORKOUT)
      vi.mocked(api.fetchComments).mockResolvedValue(
        comments([comment(501, { replies: [reply(502, { deletable: true })], replyCount: 1 })]),
      )
      renderApp('/workouts/12')

      vi.mocked(api.deleteComment).mockResolvedValue({ commentCount: 1 })
      await userEvent.click(await screen.findByRole('button', { name: '削除' }))

      const dialog = screen.getByRole('dialog')
      expect(dialog).not.toHaveTextContent('返信も削除されます')
      await userEvent.click(within(dialog).getByRole('button', { name: '削除する' }))

      expect(api.deleteComment).toHaveBeenCalledWith(502)
      await waitFor(() => expect(screen.queryByText('返信502')).not.toBeInTheDocument())
      expect(screen.getByText('コメント501')).toBeInTheDocument()
      expect(screen.getByTitle('コメント')).toHaveTextContent('1')
    })
  })
})
