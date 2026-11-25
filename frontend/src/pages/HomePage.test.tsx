import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { ExerciseSummary, WorkoutCard } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-10 ホーム（タイムライン）と投稿カード（docs/screens.md）。
// 💪 を押す操作・💬 でコメント欄へは 13、ハッシュタグのリンクは段階 4、今週のひとことは段階 2 で足す

function summary(values: Partial<ExerciseSummary>): ExerciseSummary {
  return {
    exerciseId: 1,
    name: 'ベンチプレス',
    recordType: 'weight_reps',
    personalBest: false,
    setCount: 3,
    uniform: true,
    weightKg: 60,
    reps: 10,
    totalReps: null,
    durationSec: null,
    distanceKm: null,
    paceSecPerKm: null,
    ...values,
  }
}

function card(values: Partial<WorkoutCard>): WorkoutCard {
  return {
    id: 1,
    author: { username: 'sato', displayName: '佐藤花子', iconUrl: null, isPrivate: false },
    performedOn: '2026-11-14',
    createdAt: '2026-11-14T19:00:00+09:00',
    editedAt: null,
    body: '今日は脚の日！',
    visibility: 'public',
    exerciseSummaries: [summary({})],
    otherExerciseCount: 0,
    images: [],
    likeCount: 0,
    likedByMe: false,
    commentCount: 0,
    mine: false,
    ...values,
  }
}

function page(items: WorkoutCard[], nextCursor: string | null = null) {
  return { items, nextCursor, hasNext: nextCursor !== null }
}

beforeEach(() => {
  vi.mocked(api.fetchTimeline).mockReset()
  vi.mocked(api.fetchNewPostCount).mockReset()
  vi.mocked(api.deleteWorkout).mockReset()
  vi.mocked(api.likeWorkout).mockReset()
  vi.mocked(api.unlikeWorkout).mockReset()
  startAs(ME)
})

describe('投稿カード', () => {
  test('投稿者・実施日・ひとこと・種目の要約・「ほか N 種目」・画像・数を出す', async () => {
    vi.mocked(api.fetchTimeline).mockResolvedValue(
      page([
        card({
          author: { username: 'sato', displayName: '佐藤花子', iconUrl: null, isPrivate: true },
          editedAt: '2026-11-15T10:00:00+09:00',
          visibility: 'followers',
          exerciseSummaries: [
            summary({ personalBest: true }),
            summary({ exerciseId: 16, name: 'スクワット', uniform: false, weightKg: 100, reps: 5 }),
          ],
          otherExerciseCount: 2,
          images: [{ id: 9, url: 'https://example.com/1.jpg', sortOrder: 1 }],
          likeCount: 12,
          commentCount: 3,
        }),
      ]),
    )
    renderApp('/')

    const article = await screen.findByRole('article')
    expect(within(article).getByRole('link', { name: '佐藤花子' })).toHaveAttribute(
      'href',
      '/users/sato',
    )
    expect(within(article).getByText('@sato')).toBeInTheDocument()
    expect(within(article).getByText('鍵アカウント')).toBeInTheDocument()
    expect(within(article).getByRole('link', { name: /11\/14/ })).toHaveAttribute(
      'href',
      '/workouts/1',
    )
    expect(within(article).getByText('編集済み')).toBeInTheDocument()
    expect(within(article).getByText('フォロワー限定')).toBeInTheDocument()
    expect(within(article).getByText('今日は脚の日！')).toBeInTheDocument()
    const lines = within(article).getAllByRole('listitem')
    expect(lines[0]).toHaveTextContent('ベンチプレス60kg × 10回 × 3セット🏆自己ベスト更新')
    expect(lines[1]).toHaveTextContent('スクワット3セット（最大 100kg × 5回）')
    expect(within(article).getByText('ほか 2 種目')).toBeInTheDocument()
    expect(within(article).getByRole('img', { name: '画像 1 / 1' })).toHaveAttribute(
      'src',
      'https://example.com/1.jpg',
    )
    expect(within(article).getByTitle('コメント')).toHaveTextContent('3')
    expect(within(article).getByTitle('ナイストレーニング')).toHaveTextContent('12')
    // 他人の記録には `…` メニューを出さない
    expect(within(article).queryByRole('button', { name: 'メニュー' })).not.toBeInTheDocument()
  })

  test('数が 0 なら数字を出さない', async () => {
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({})]))
    renderApp('/')

    const article = await screen.findByRole('article')
    expect(within(article).getByTitle('コメント')).toHaveTextContent(/^💬コメント$/)
    expect(within(article).getByTitle('ナイストレーニング')).toHaveTextContent(
      /^💪ナイストレーニング$/,
    )
    // 5種目以下なら「ほか N 種目」を出さない
    expect(within(article).queryByText(/^ほか/)).not.toBeInTheDocument()
  })

  test('カードの余白を押すと投稿詳細へ。リンク・ボタンを押したときはそちらを優先する', async () => {
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({})]))
    vi.mocked(api.fetchWorkout).mockReturnValue(new Promise(() => {}))
    const { router } = renderApp('/')

    await userEvent.click(await screen.findByText('今日は脚の日！'))

    expect(router.state.location.pathname).toBe('/workouts/1')
  })

  test('投稿者を押すと、詳細ではなくプロフィールへ', async () => {
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({})]))
    vi.mocked(api.fetchProfile).mockReturnValue(new Promise(() => {}))
    const { router } = renderApp('/')

    await userEvent.click(await screen.findByRole('link', { name: '佐藤花子' }))

    expect(router.state.location.pathname).toBe('/users/sato')
  })

  test('自分の記録は `…` メニューから削除でき、一覧から消える', async () => {
    vi.mocked(api.fetchTimeline)
      .mockResolvedValueOnce(page([card({ mine: true })]))
      .mockResolvedValue(page([]))
    vi.mocked(api.deleteWorkout).mockResolvedValue(undefined)
    const { router } = renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: 'メニュー' }))
    await userEvent.click(screen.getByRole('menuitem', { name: '削除' }))
    await userEvent.click(
      within(screen.getByRole('dialog')).getByRole('button', { name: '削除する' }),
    )

    expect(api.deleteWorkout).toHaveBeenCalledWith(1)
    expect(await screen.findByText('記録を削除しました')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('article')).not.toBeInTheDocument())
    expect(router.state.location.pathname).toBe('/')
  })
})

describe('ナイストレーニング', () => {
  const likeButton = () => screen.getByRole('button', { name: /ナイストレーニング/ })

  test('押すとすぐ付けた状態にして数を増やし、送り終えたら返ってきた数に合わせる', async () => {
    let resolve: (state: { likeCount: number; likedByMe: boolean }) => void = () => {}
    vi.mocked(api.likeWorkout).mockReturnValue(new Promise((r) => (resolve = r)))
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({ likeCount: 0 })]))
    const { router } = renderApp('/')
    await screen.findByRole('article')
    expect(likeButton()).toHaveAttribute('aria-pressed', 'false')

    await userEvent.click(likeButton())

    expect(likeButton()).toHaveAttribute('aria-pressed', 'true')
    expect(likeButton()).toHaveTextContent(/^💪ナイストレーニング1$/)
    // 送信中は押せない（連打で2回送らない）
    expect(likeButton()).toBeDisabled()
    expect(api.likeWorkout).toHaveBeenCalledWith(1)
    // ボタンを押しても、詳細には移らない
    expect(router.state.location.pathname).toBe('/')

    resolve({ likeCount: 3, likedByMe: true })
    await waitFor(() => expect(likeButton()).toHaveTextContent(/3$/))
    expect(likeButton()).toBeEnabled()
  })

  test('付けていれば、押すと取り消して数を減らす', async () => {
    vi.mocked(api.unlikeWorkout).mockResolvedValue({ likeCount: 11, likedByMe: false })
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({ likeCount: 12, likedByMe: true })]))
    renderApp('/')
    await screen.findByRole('article')
    expect(likeButton()).toHaveAttribute('aria-pressed', 'true')

    await userEvent.click(likeButton())

    expect(api.unlikeWorkout).toHaveBeenCalledWith(1)
    expect(api.likeWorkout).not.toHaveBeenCalled()
    expect(likeButton()).toHaveAttribute('aria-pressed', 'false')
    await waitFor(() => expect(likeButton()).toHaveTextContent(/11$/))
  })

  test('送れなければ元に戻し、お知らせで理由を出す', async () => {
    vi.mocked(api.likeWorkout).mockRejectedValue(
      new api.ApiError(404, 'WORKOUT_NOT_FOUND', '記録が見つかりません'),
    )
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({ likeCount: 2 })]))
    renderApp('/')
    await screen.findByRole('article')

    await userEvent.click(likeButton())

    expect(await screen.findByText('記録が見つかりません')).toBeInTheDocument()
    expect(likeButton()).toHaveAttribute('aria-pressed', 'false')
    expect(likeButton()).toHaveTextContent(/2$/)
  })

  test('詳細で押した結果は、タイムラインに戻っても同じ', async () => {
    vi.mocked(api.likeWorkout).mockResolvedValue({ likeCount: 1, likedByMe: true })
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({ likeCount: 0 })]))
    vi.mocked(api.fetchWorkout).mockResolvedValue({
      ...card({ likeCount: 0 }),
      durationMin: null,
      exercises: [],
    })
    const { router } = renderApp('/')
    await userEvent.click(await screen.findByText('今日は脚の日！'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/workouts/1'))
    await userEvent.click(await screen.findByRole('button', { name: /ナイストレーニング/ }))
    await waitFor(() => expect(api.likeWorkout).toHaveBeenCalled())

    await router.navigate(-1)

    await waitFor(() => expect(router.state.location.pathname).toBe('/'))
    expect(await screen.findByRole('article', { name: '佐藤花子の記録' })).toBeInTheDocument()
    expect(likeButton()).toHaveAttribute('aria-pressed', 'true')
    expect(likeButton()).toHaveTextContent(/1$/)
  })
})

describe('タイムライン', () => {
  test('フォロー中を出し、タブで全体に切り替える（履歴は増やさない）', async () => {
    vi.mocked(api.fetchTimeline).mockImplementation(async (kind) =>
      page([
        card({
          id: kind === 'all' ? 2 : 1,
          body: kind === 'all' ? '全体の記録' : 'フォロー中の記録',
        }),
      ]),
    )
    const { router } = renderApp('/')

    expect(await screen.findByText('フォロー中の記録')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'フォロー中', selected: true })).toBeInTheDocument()
    expect(api.fetchTimeline).toHaveBeenCalledWith('following', undefined)

    await userEvent.click(screen.getByRole('tab', { name: '全体' }))

    expect(await screen.findByText('全体の記録')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '全体', selected: true })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/all')
    expect(router.state.historyAction).toBe('REPLACE')
  })

  test('続きがあれば読み込み、最後まで読むと「これ以上の投稿はありません」', async () => {
    vi.mocked(api.fetchTimeline)
      .mockResolvedValueOnce(page([card({ id: 1, body: '1件目' })], 'next'))
      .mockResolvedValueOnce(page([card({ id: 2, body: '2件目' })]))
    renderApp('/')

    expect(await screen.findByText('1件目')).toBeInTheDocument()
    expect(screen.queryByText('これ以上の投稿はありません')).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'さらに表示' }))

    expect(await screen.findByText('2件目')).toBeInTheDocument()
    expect(api.fetchTimeline).toHaveBeenLastCalledWith('following', 'next')
    expect(screen.getByText('これ以上の投稿はありません')).toBeInTheDocument()
  })

  test('フォロー中が0件なら、記録とフォローを勧める', async () => {
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([]))
    renderApp('/')

    expect(
      await screen.findByText('まだ記録がありません。最初の記録をつけてみましょう'),
    ).toBeInTheDocument()
    expect(
      within(screen.getByRole('main')).getByRole('link', { name: '＋ 記録する' }),
    ).toHaveAttribute('href', '/workouts/new')
    expect(screen.getByText('ユーザーを検索して、仲間をフォローしてみましょう')).toBeInTheDocument()
  })

  test('全体が0件なら「まだ誰も記録していません」', async () => {
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([]))
    renderApp('/all')

    expect(await screen.findByText('まだ誰も記録していません')).toBeInTheDocument()
  })

  test('読み込めなければ理由と「もう一度読み込む」を出す', async () => {
    // 通信の失敗・5xx は自動でやり直すので、やり直さない 4xx で確かめる
    vi.mocked(api.fetchTimeline)
      .mockRejectedValueOnce(new api.ApiError(400, 'VALIDATION_FAILED', '読み込めませんでした'))
      .mockResolvedValueOnce(page([card({})]))
    renderApp('/')

    expect(await screen.findByText('読み込めませんでした')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(await screen.findByRole('article')).toBeInTheDocument()
  })
})

describe('新しい投稿のお知らせ', () => {
  test('一番新しい記録より後の件数を確かめ、押すと最新を取り直してボタンを消す', async () => {
    vi.mocked(api.fetchTimeline)
      .mockResolvedValueOnce(page([card({ id: 5, body: '古い記録' })]))
      .mockResolvedValue(
        page([card({ id: 8, body: '新しい記録' }), card({ id: 5, body: '古い記録' })]),
      )
    vi.mocked(api.fetchNewPostCount)
      .mockResolvedValueOnce({ count: 3 })
      .mockResolvedValue({ count: 0 })
    renderApp('/')

    await userEvent.click(await screen.findByRole('button', { name: '↑ 3件の新しい投稿' }))

    expect(api.fetchNewPostCount).toHaveBeenCalledWith('following', 5)
    expect(await screen.findByText('新しい記録')).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: /新しい投稿/ })).not.toBeInTheDocument(),
    )
  })

  test('取り直すときは、読み込んだ続きのページを捨てて1ページ目から読む', async () => {
    vi.mocked(api.fetchTimeline)
      .mockResolvedValueOnce(page([card({ id: 5, body: '1ページ目' })], 'next'))
      .mockResolvedValueOnce(page([card({ id: 4, body: '2ページ目' })]))
      .mockResolvedValue(page([card({ id: 8, body: '新しい記録' })], 'next2'))
    vi.mocked(api.fetchNewPostCount).mockResolvedValue({ count: 0 })
    renderApp('/')
    await userEvent.click(await screen.findByRole('button', { name: 'さらに表示' }))
    expect(await screen.findByText('2ページ目')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'フォロー中' }))

    expect(await screen.findByText('新しい記録')).toBeInTheDocument()
    expect(screen.queryByText('2ページ目')).not.toBeInTheDocument()
    expect(api.fetchTimeline).toHaveBeenCalledTimes(3)
  })

  test('60秒ごとに件数を確かめ直す', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({})]))
      vi.mocked(api.fetchNewPostCount).mockResolvedValue({ count: 0 })
      renderApp('/')
      await waitFor(() => expect(api.fetchNewPostCount).toHaveBeenCalledTimes(1))

      await vi.advanceTimersByTimeAsync(59_000)
      expect(api.fetchNewPostCount).toHaveBeenCalledTimes(1)
      await vi.advanceTimersByTimeAsync(1_000)
      await waitFor(() => expect(api.fetchNewPostCount).toHaveBeenCalledTimes(2))
    } finally {
      vi.useRealTimers()
    }
  })

  test('全体のタブでは全体の件数を確かめる。0件ならボタンを出さない', async () => {
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({ id: 7 })]))
    vi.mocked(api.fetchNewPostCount).mockResolvedValue({ count: 0 })
    renderApp('/all')

    await waitFor(() => expect(api.fetchNewPostCount).toHaveBeenCalledWith('all', 7))
    expect(screen.queryByRole('button', { name: /新しい投稿/ })).not.toBeInTheDocument()
  })

  test('100件なら「99+件」', async () => {
    vi.mocked(api.fetchTimeline).mockResolvedValue(page([card({})]))
    vi.mocked(api.fetchNewPostCount).mockResolvedValue({ count: 100 })
    renderApp('/')

    expect(await screen.findByRole('button', { name: '↑ 99+件の新しい投稿' })).toBeInTheDocument()
  })

  test('ナビの「ホーム」を表示中にもう一度押すと、最新を取り直す', async () => {
    vi.mocked(api.fetchTimeline)
      .mockResolvedValueOnce(page([card({ body: '古い記録' })]))
      .mockResolvedValue(page([card({ id: 2, body: '新しい記録' })]))
    vi.mocked(api.fetchNewPostCount).mockResolvedValue({ count: 0 })
    renderApp('/')
    expect(await screen.findByText('古い記録')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('link', { name: /ホーム/ }))

    expect(await screen.findByText('新しい記録')).toBeInTheDocument()
  })

  test('表示中のタブをもう一度押しても、最新を取り直す', async () => {
    vi.mocked(api.fetchTimeline)
      .mockResolvedValueOnce(page([card({ body: '古い記録' })]))
      .mockResolvedValue(page([card({ id: 2, body: '新しい記録' })]))
    vi.mocked(api.fetchNewPostCount).mockResolvedValue({ count: 0 })
    renderApp('/')
    expect(await screen.findByText('古い記録')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('tab', { name: 'フォロー中' }))

    expect(await screen.findByText('新しい記録')).toBeInTheDocument()
  })
})
