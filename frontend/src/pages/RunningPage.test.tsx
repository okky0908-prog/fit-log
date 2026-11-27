import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { RunningStats, RunningSummary } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)
// Recharts は jsdom では大きさが測れず描けないので、グラフの部品は偽物にする（値は「表で見る」の表で確かめる）
vi.mock('../components/StatsBarChart', () => ({
  default: ({ title }: { title: string }) => <div data-testid="bar-chart">{title}</div>,
}))

// S-21 ランニングの記録（docs/screens.md S-21、F-83）。通信の関数が呼ばれたかは waitFor で待って確かめる

/** 日本時間 2026-11-12（木） */
const NOW = new Date('2026-11-12T10:00:00+09:00')

function summary(values: Partial<RunningSummary> = {}): RunningSummary {
  return { distanceKm: 0, durationSec: 0, runCount: 0, avgPaceSecPerKm: null, ...values }
}

function stats(from: string, to: string, values: Partial<RunningStats> = {}): RunningStats {
  return {
    from,
    to,
    summary: summary(),
    previous: summary(),
    bestPace: null,
    longestRun: null,
    monthly: [],
    ...values,
  }
}

const NOVEMBER = stats('2026-11-01', '2026-11-30', {
  summary: summary({ distanceKm: 45.2, durationSec: 16270, runCount: 8, avgPaceSecPerKm: 360 }),
  previous: summary({ distanceKm: 40.1, durationSec: 14770, runCount: 7, avgPaceSecPerKm: 368 }),
  bestPace: {
    workoutId: 120,
    performedOn: '2026-11-08',
    distanceKm: 10,
    durationSec: 3120,
    paceSecPerKm: 312,
  },
  longestRun: {
    workoutId: 101,
    performedOn: '2026-11-03',
    distanceKm: 15,
    durationSec: 5720,
    paceSecPerKm: 381,
  },
})

const YEAR = stats('2026-01-01', '2026-12-31', {
  summary: summary({ distanceKm: 60, durationSec: 21600, runCount: 10, avgPaceSecPerKm: 360 }),
  monthly: Array.from({ length: 12 }, (_, i) => ({
    month: `2026-${String(i + 1).padStart(2, '0')}`,
    distanceKm: i === 9 ? 14.8 : i === 10 ? 45.2 : 0,
  })),
})

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  vi.mocked(api.fetchRunningStats).mockReset()
  vi.mocked(api.fetchRunningStats).mockImplementation(async (period, date) => {
    if (period === 'month' && date === '2026-11-01') return NOVEMBER
    if (period === 'year' && date === '2026-01-01') return YEAR
    return stats(date, date)
  })
  startAs(ME)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('月間（開いたときの期間）', () => {
  test('今月の走行距離・走行時間・回数・平均ペースと、前の月との差を出す', async () => {
    renderApp('/records/running')

    expect(await screen.findByRole('heading', { level: 1, name: 'ランニング' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '月間' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByText('2026年11月')).toBeInTheDocument()
    await waitFor(() => expect(api.fetchRunningStats).toHaveBeenCalledWith('month', '2026-11-01'))

    const items = within(await screen.findByRole('region', { name: '集計' })).getAllByRole(
      'definition',
    )
    expect(items.map((item) => item.textContent)).toEqual([
      '45.20km 前の月との差 +5.10',
      '4:31:10 前の月との差 +25:00',
      '8回 前の月との差 +1',
      '6:00/km 前の月との差 −0:08',
    ])
  })

  test('ベスト記録を出し、押すとその記録の詳細へ移る', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/running')

    const best = await screen.findByRole('region', { name: 'ベスト記録' })
    expect(within(best).getByText('（1km 以上の記録から）')).toBeInTheDocument()
    expect(
      within(best).getByRole('link', { name: /最速ペース 5:12\/km（10\.00km・52:00・11\/8）/ }),
    ).toHaveAttribute('href', '/workouts/120')
    await user.click(
      within(best).getByRole('link', { name: /最長距離 15\.00km（1:35:20・11\/3）/ }),
    )

    expect(router.state.location.pathname).toBe('/workouts/101')
  })

  test('月間・週間では、月ごとの走行距離のグラフを出さない', async () => {
    renderApp('/records/running')
    await screen.findByRole('region', { name: 'ベスト記録' })
    expect(screen.queryByRole('region', { name: '月ごとの走行距離' })).not.toBeInTheDocument()
  })

  test('マイル表示の設定なら、距離とペースをマイルで出す', async () => {
    startAs({ ...ME, distanceUnit: 'mi' })
    renderApp('/records/running')

    const region = await screen.findByRole('region', { name: '集計' })
    expect(within(region).getByText('28.09mi')).toBeInTheDocument()
    expect(within(region).getByText('9:39/mi')).toBeInTheDocument()
  })
})

describe('期間の切り替え', () => {
  test('‹ で前の月へ移り、「今月」で戻る。今月より先へは移れない', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/running')
    await screen.findByText('2026年11月')
    expect(screen.queryByRole('link', { name: '次の月' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: '今月' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: '前の月' }))

    expect(await screen.findByText('2026年10月')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?period=month&date=2026-10-01')
    await waitFor(() => expect(api.fetchRunningStats).toHaveBeenCalledWith('month', '2026-10-01'))
    await user.click(screen.getByRole('link', { name: '次の月' }))
    expect(await screen.findByText('2026年11月')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?period=month')

    await user.click(screen.getByRole('link', { name: '前の月' }))
    await user.click(await screen.findByRole('link', { name: '今月' }))
    expect(await screen.findByText('2026年11月')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?period=month')
  })

  test('週間のタブでは今週を出し、前の週へ移れる', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/running')
    await screen.findByText('2026年11月')

    await user.click(screen.getByRole('tab', { name: '週間' }))

    expect(screen.getByRole('tab', { name: '週間' })).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findByText('2026/11/09（月）〜 11/15（日）')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?period=week')
    await waitFor(() => expect(api.fetchRunningStats).toHaveBeenCalledWith('week', '2026-11-09'))
    await user.click(screen.getByRole('link', { name: '前の週' }))
    expect(await screen.findByText('2026/11/02（月）〜 11/08（日）')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '今週' })).toBeInTheDocument()
  })

  test('タブを切り替えても、見ている日を含む期間を出す', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/running?period=month&date=2025-10-01')
    await screen.findByText('2025年10月')

    await user.click(screen.getByRole('tab', { name: '週間' }))
    expect(await screen.findByText('2025/09/29（月）〜 10/05（日）')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?period=week&date=2025-09-29')

    await user.click(screen.getByRole('tab', { name: '年間' }))
    expect(await screen.findByText('2025年')).toBeInTheDocument()
    expect(router.state.location.search).toBe('?period=year&date=2025-01-01')
    expect(screen.getByRole('link', { name: '今年' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '次の年' })).toBeInTheDocument()
  })

  test('URL の期間を開く。未来の期間は今の期間にする', async () => {
    renderApp('/records/running?period=week&date=2026-12-01')
    expect(await screen.findByText('2026/11/09（月）〜 11/15（日）')).toBeInTheDocument()
    await waitFor(() => expect(api.fetchRunningStats).toHaveBeenCalledWith('week', '2026-11-09'))
  })
})

describe('年間', () => {
  test('月ごとの走行距離のグラフを出し、「表で見る」で同じ値を表で読める', async () => {
    const user = userEvent.setup()
    renderApp('/records/running?period=year')

    expect(await screen.findByText('2026年')).toBeInTheDocument()
    await waitFor(() => expect(api.fetchRunningStats).toHaveBeenCalledWith('year', '2026-01-01'))
    const chart = await screen.findByRole('region', { name: '月ごとの走行距離' })
    expect(await within(chart).findByTestId('bar-chart')).toHaveTextContent('月ごとの走行距離')
    await user.click(within(chart).getByRole('button', { name: '表で見る' }))
    const rows = within(within(chart).getByRole('table')).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(12)
    expect(rows[0]).toHaveTextContent('1月0.00km')
    expect(rows[9]).toHaveTextContent('10月14.80km')
    expect(rows[10]).toHaveTextContent('11月45.20km')
  })
})

describe('記録がないとき・読み込めないとき', () => {
  test('ランニングの記録がない期間は、集計とベスト記録の代わりにそう出す', async () => {
    renderApp('/records/running?period=month&date=2026-09-01')

    expect(await screen.findByText('この期間のランニングの記録はありません')).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '集計' })).not.toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'ベスト記録' })).not.toBeInTheDocument()
  })

  test('距離のない記録だけなら、平均ペースとベスト記録は「—」', async () => {
    vi.mocked(api.fetchRunningStats).mockResolvedValue(
      stats('2026-11-01', '2026-11-30', {
        summary: summary({ durationSec: 1800, runCount: 1 }),
      }),
    )
    renderApp('/records/running')

    const region = await screen.findByRole('region', { name: '集計' })
    expect(within(region).getAllByRole('definition')[3]).toHaveTextContent(/^—$/)
    const best = screen.getByRole('region', { name: 'ベスト記録' })
    expect(within(best).queryByRole('link')).not.toBeInTheDocument()
    expect(within(best).getByText('最速ペース').parentElement).toHaveTextContent('最速ペース —')
    expect(within(best).getByText('最長距離').parentElement).toHaveTextContent('最長距離 —')
  })

  test('読み込みに失敗したら、もう一度読み込める', async () => {
    const user = userEvent.setup()
    // 通信の失敗・5xx は自動でやり直すので、やり直さないエラー（4xx）で確かめる
    vi.mocked(api.fetchRunningStats).mockRejectedValueOnce(
      new api.ApiError(400, 'MALFORMED_REQUEST', '読み込めませんでした'),
    )
    renderApp('/records/running')

    expect(await screen.findByText('読み込めませんでした')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))

    expect(await screen.findByText('45.20km')).toBeInTheDocument()
  })
})

describe('ほかの画面との行き来', () => {
  test('記録の画面の「ランニング ›」から開き、「← 記録」で戻る', async () => {
    const user = userEvent.setup()
    vi.mocked(api.fetchWeeklyStats).mockRejectedValue(new api.ApiError(400, 'X', 'x'))
    vi.mocked(api.fetchCalendar).mockResolvedValue({ month: '2026-11', dates: [] })
    const { router } = renderApp('/records')

    await user.click(await screen.findByRole('link', { name: 'ランニング ›' }))
    expect(await screen.findByRole('heading', { level: 1, name: 'ランニング' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/records/running')

    await user.click(screen.getByRole('button', { name: '記録（戻る）' }))
    expect(router.state.location.pathname).toBe('/records')
  })
})
