import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { MonthlyStats, StatsSummary, WeeklyStats, WorkoutCard } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)
// Recharts は jsdom では大きさが測れず描けないので、グラフの部品は偽物にする（値は「表で見る」の表で確かめる）
vi.mock('../components/StatsBarChart', () => ({
  default: ({ title }: { title: string }) => <div data-testid="bar-chart">{title}</div>,
}))

// S-20 記録（ダッシュボード）の週間・月間（docs/screens.md）。カレンダーは 2-4、目標は段階 3、消費カロリーは段階 5 で足す

/** 日本時間 2026-11-12（木） */
const NOW = new Date('2026-11-12T10:00:00+09:00')

function summary(values: Partial<StatsSummary> = {}): StatsSummary {
  return {
    trainingDays: 0,
    totalVolumeKg: 0,
    totalDurationSec: 0,
    totalDistanceKm: 0,
    caloriesKcal: null,
    caloriesExcludedWorkoutCount: 0,
    ...values,
  }
}

function weekly(from: string, values: Partial<WeeklyStats> = {}): WeeklyStats {
  const days = Array.from({ length: 7 }, (_, i) => {
    const date = new Date(`${from}T00:00:00Z`)
    date.setUTCDate(date.getUTCDate() + i)
    return { date: date.toISOString().slice(0, 10), volumeKg: 0, durationSec: 0 }
  })
  return {
    from,
    to: days[6].date,
    summary: summary(),
    previous: summary(),
    daily: days,
    bodyParts: [],
    ...values,
  }
}

const THIS_WEEK = weekly('2026-11-09', {
  summary: summary({
    trainingDays: 4,
    totalVolumeKg: 12450,
    totalDurationSec: 5700,
    totalDistanceKm: 15,
  }),
  previous: summary({
    trainingDays: 3,
    totalVolumeKg: 11650,
    totalDurationSec: 6300,
    totalDistanceKm: 12,
  }),
  daily: [
    { date: '2026-11-09', volumeKg: 3200, durationSec: 0 },
    { date: '2026-11-10', volumeKg: 0, durationSec: 1800 },
    { date: '2026-11-11', volumeKg: 0, durationSec: 0 },
    { date: '2026-11-12', volumeKg: 9250, durationSec: 3900 },
    { date: '2026-11-13', volumeKg: 0, durationSec: 0 },
    { date: '2026-11-14', volumeKg: 0, durationSec: 0 },
    { date: '2026-11-15', volumeKg: 0, durationSec: 0 },
  ],
  bodyParts: [
    { bodyPart: 'chest', setCount: 12 },
    { bodyPart: 'legs', setCount: 9 },
    { bodyPart: 'cardio', setCount: 19 },
  ],
})

const NOVEMBER: MonthlyStats = {
  from: '2026-11-01',
  to: '2026-11-30',
  summary: summary({ trainingDays: 2, totalVolumeKg: 600 }),
  previous: summary({ trainingDays: 1, totalVolumeKg: 500 }),
  weekly: ['2026-10-26', '2026-11-02', '2026-11-09', '2026-11-16', '2026-11-23', '2026-11-30'].map(
    (weekStart, i) => ({
      weekStart,
      volumeKg: i === 0 ? 600 : 0,
      durationSec: i === 5 ? 1800 : 0,
      distanceKm: 0,
      trainingDays: i === 0 || i === 5 ? 1 : 0,
    }),
  ),
  bodyParts: [{ bodyPart: 'chest', setCount: 1 }],
}

/** 記録が1件以上あることを表す、自分の記録一覧の1件 */
const SOME_WORKOUT = { id: 1 } as WorkoutCard

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  vi.mocked(api.fetchWeeklyStats).mockReset()
  vi.mocked(api.fetchMonthlyStats).mockReset()
  vi.mocked(api.fetchWeeklyStats).mockImplementation(async (date) =>
    date === '2026-11-09' ? THIS_WEEK : weekly(date),
  )
  vi.mocked(api.fetchMonthlyStats).mockResolvedValue(NOVEMBER)
  vi.mocked(api.fetchUserWorkouts).mockResolvedValue({
    items: [SOME_WORKOUT],
    nextCursor: null,
    hasNext: false,
  })
  startAs(ME)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('週間', () => {
  test('今週の集計値と前の週との差、部位別のセット数を出す', async () => {
    renderApp('/records')

    expect(await screen.findByRole('heading', { name: '記録' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '週間' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('2026/11/09（月）〜 11/15（日）')).toBeInTheDocument()
    expect(api.fetchWeeklyStats).toHaveBeenCalledWith('2026-11-09')

    const stats = await screen.findByRole('region', { name: '集計' })
    expect(within(stats).getByText('4日')).toBeInTheDocument()
    expect(within(stats).getByText('+1')).toBeInTheDocument()
    expect(within(stats).getByText('12,450kg')).toBeInTheDocument()
    expect(within(stats).getByText('+800')).toBeInTheDocument()
    expect(within(stats).getByText('95分')).toBeInTheDocument()
    expect(within(stats).getByText('−10')).toBeInTheDocument()
    expect(within(stats).getByText('15.00km')).toBeInTheDocument()

    const parts = screen.getByRole('region', { name: '部位別のセット数' })
    expect(
      within(parts)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['胸12セット（30%）', '脚9セット（23%）', '有酸素19セット（48%）'])
  })

  test('曜日ごとのグラフは「表で見る」で同じ値を表で読め、運動時間に切り替えられる', async () => {
    const user = userEvent.setup()
    renderApp('/records')

    const chart = await screen.findByRole('region', { name: '曜日ごと' })
    expect(await within(chart).findByTestId('bar-chart')).toHaveTextContent('曜日ごとの総挙上量')
    await user.click(within(chart).getByRole('button', { name: '表で見る' }))
    expect(rows(chart)).toEqual([
      ['月', '3,200kg'],
      ['火', '0kg'],
      ['水', '0kg'],
      ['木', '9,250kg'],
      ['金', '0kg'],
      ['土', '0kg'],
      ['日', '0kg'],
    ])

    await user.click(within(chart).getByRole('button', { name: '運動時間' }))
    expect(within(chart).getByRole('button', { name: '運動時間' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(rows(chart)[1]).toEqual(['火', '30分'])
    expect(rows(chart)[3]).toEqual(['木', '65分'])
  })

  test('‹ で前の週へ移り、「今週」で戻る。今週より先へは移れない', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records')
    await screen.findByText('2026/11/09（月）〜 11/15（日）')
    expect(screen.queryByRole('link', { name: '次の週' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '今週' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: '前の週' }))

    expect(await screen.findByText('2026/11/02（月）〜 11/08（日）')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?week=2026-11-02')
    expect(api.fetchWeeklyStats).toHaveBeenCalledWith('2026-11-02')
    await user.click(screen.getByRole('link', { name: '次の週' }))
    expect(await screen.findByText('2026/11/09（月）〜 11/15（日）')).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: '前の週' }))
    await user.click(await screen.findByRole('link', { name: '今週' }))
    expect(await screen.findByText('2026/11/09（月）〜 11/15（日）')).toBeInTheDocument()
    expect(router.state.location.search).toBe('')
  })

  test('URL の週を開く。未来の週は今週にする', async () => {
    renderApp('/records?week=2026-10-28')
    expect(await screen.findByText('2026/10/26（月）〜 11/01（日）')).toBeInTheDocument()
    expect(api.fetchWeeklyStats).toHaveBeenCalledWith('2026-10-26')
  })

  test('この期間の記録がなければ、グラフと部位別の代わりにそう出す', async () => {
    renderApp('/records?week=2026-11-02')

    const chart = await screen.findByRole('region', { name: '曜日ごと' })
    expect(await within(chart).findByText('この期間の記録はありません')).toBeInTheDocument()
    expect(within(chart).queryByRole('button', { name: '表で見る' })).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('region', { name: '部位別のセット数' })).getByText(
        'この期間の記録はありません',
      ),
    ).toBeInTheDocument()
  })

  test('読み込みに失敗したら、もう一度読み込める', async () => {
    const user = userEvent.setup()
    // 通信の失敗・5xx は自動でやり直すので、やり直さないエラー（4xx）で確かめる
    vi.mocked(api.fetchWeeklyStats).mockRejectedValueOnce(
      new api.ApiError(400, 'VALIDATION_FAILED', '読み込めませんでした'),
    )
    renderApp('/records')

    await user.click(await screen.findByRole('button', { name: 'もう一度読み込む' }))

    expect(await screen.findByText('12,450kg')).toBeInTheDocument()
  })
})

describe('月間', () => {
  test('今月の集計と、週ごとのグラフ（月をまたぐ週は、その月の最初の日から）', async () => {
    const user = userEvent.setup()
    renderApp('/records/month')

    expect(await screen.findByText('2026年11月')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '月間' })).toHaveAttribute('aria-selected', 'true')
    expect(api.fetchMonthlyStats).toHaveBeenCalledWith('2026-11')
    expect(await screen.findByText('600kg')).toBeInTheDocument()

    const chart = screen.getByRole('region', { name: '週ごと' })
    await user.click(within(chart).getByRole('button', { name: '表で見る' }))
    expect(rows(chart).map((row) => row[0])).toEqual([
      '11/1〜',
      '11/2〜',
      '11/9〜',
      '11/16〜',
      '11/23〜',
      '11/30〜',
    ])
    expect(rows(chart)[0]).toEqual(['11/1〜', '600kg'])
  })

  test('‹ で前の月へ（URL に入れる）。今月より先へは移れない', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/month')
    await screen.findByText('2026年11月')
    expect(screen.queryByRole('link', { name: '次の月' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: '前の月' }))

    expect(await screen.findByText('2026年10月')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?month=2026-10')
    expect(api.fetchMonthlyStats).toHaveBeenCalledWith('2026-10')
    expect(screen.getByRole('link', { name: '今月' })).toBeInTheDocument()
  })
})

test('記録が1件もなければ、集計の代わりに記録を促す', async () => {
  vi.mocked(api.fetchUserWorkouts).mockResolvedValue({
    items: [],
    nextCursor: null,
    hasNext: false,
  })
  renderApp('/records')

  expect(
    await screen.findByText('記録をつけると、ここに週・月の集計が表示されます'),
  ).toBeInTheDocument()
  expect(screen.getByRole('link', { name: '＋ 記録する' })).toHaveAttribute('href', '/workouts/new')
  expect(screen.queryByRole('region', { name: '集計' })).not.toBeInTheDocument()
  expect(api.fetchUserWorkouts).toHaveBeenCalledWith(ME.username)
})

test('ナビゲーションの「記録」は、週間・月間のどちらでも選ばれた見た目にする', async () => {
  renderApp('/records/month')

  const nav = await screen.findByRole('navigation', { name: 'メイン' })
  expect(within(nav).getByRole('link', { name: '記録' })).toHaveAttribute('aria-current', 'page')
  expect(within(nav).getByRole('link', { name: '記録' })).toHaveAttribute('href', '/records')
})

/** 「表で見る」の表の行（見出しの行を除く） */
function rows(region: HTMLElement): string[][] {
  return within(within(region).getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) =>
      within(row)
        .getAllByRole('cell')
        .map((cell) => cell.textContent ?? ''),
    )
}
