import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { Exercise, ExerciseTrend, TrendPoint, UsedExercise } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)
// Recharts は jsdom では大きさが測れず描けないので、グラフの部品は偽物にする（値は「表で見る」の表で確かめる）
vi.mock('../components/StatsLineChart', () => ({
  default: ({ title }: { title: string }) => <div data-testid="line-chart">{title}</div>,
}))
vi.mock('../components/StatsBarChart', () => ({
  default: ({ title }: { title: string }) => <div data-testid="bar-chart">{title}</div>,
}))

// S-22 種目ごとの推移（docs/screens.md S-22、F-84・F-85）。通信の関数が呼ばれたかは waitFor で待って確かめる

const NOW = new Date('2026-11-12T10:00:00+09:00')

const BENCH: Exercise = {
  id: 1,
  name: 'ベンチプレス',
  bodyPart: 'chest',
  recordType: 'weight_reps',
  isRunning: false,
  custom: false,
}
const PUSH_UP: Exercise = { ...BENCH, id: 8, name: '腕立て伏せ', recordType: 'reps' }
const RUN: Exercise = {
  ...BENCH,
  id: 48,
  name: 'ランニング',
  bodyPart: 'cardio',
  recordType: 'time_distance',
  isRunning: true,
}

const USED: UsedExercise[] = [
  { exercise: BENCH, lastPerformedOn: '2026-11-10' },
  { exercise: PUSH_UP, lastPerformedOn: '2026-11-01' },
  { exercise: RUN, lastPerformedOn: '2026-10-01' },
]

function point(values: Partial<TrendPoint>): TrendPoint {
  return {
    date: '2026-11-14',
    workoutId: 1,
    maxWeightKg: null,
    maxE1rmKg: null,
    maxWeightReps: null,
    totalReps: null,
    maxReps: null,
    totalDurationSec: null,
    totalDistanceKm: null,
    paceSecPerKm: null,
    ...values,
  }
}

const BENCH_TREND: ExerciseTrend = {
  exercise: BENCH,
  personalBest: { weightKg: 100, performedOn: '2026-10-20', workoutId: 98 },
  bestE1rm: { e1rmKg: 112.5, performedOn: '2026-11-02', workoutId: 104 },
  points: [
    point({
      date: '2026-09-01',
      workoutId: 80,
      maxWeightKg: 85,
      maxWeightReps: 5,
      maxE1rmKg: 95.2,
    }),
    point({
      date: '2026-11-02',
      workoutId: 104,
      maxWeightKg: 95,
      maxWeightReps: 3,
      maxE1rmKg: 112.5,
    }),
  ],
}

const RUN_TREND: ExerciseTrend = {
  exercise: RUN,
  personalBest: null,
  bestE1rm: null,
  points: [
    point({ workoutId: 120, totalDurationSec: 1800, totalDistanceKm: 5, paceSecPerKm: 360 }),
  ],
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  vi.mocked(api.fetchUsedExercises).mockReset()
  vi.mocked(api.fetchUsedExercises).mockResolvedValue(USED)
  vi.mocked(api.fetchExerciseTrend).mockReset()
  vi.mocked(api.fetchExerciseTrend).mockImplementation(async (id, range) => {
    if (id === 1) return range === '3m' ? BENCH_TREND : { ...BENCH_TREND, points: [] }
    if (id === 48) return RUN_TREND
    if (id === 8) return { exercise: PUSH_UP, personalBest: null, bestE1rm: null, points: [] }
    throw new api.ApiError(404, 'EXERCISE_NOT_FOUND', '種目が見つかりません')
  })
  startAs(ME)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('記録した種目の一覧', () => {
  test('部位ごとに、最後に記録した日つきで出し、選ぶと推移へ移る', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/exercises')

    expect(
      await screen.findByRole('heading', { level: 1, name: '種目ごとの推移' }),
    ).toBeInTheDocument()
    const chest = await screen.findByRole('region', { name: '胸' })
    expect(
      within(chest)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['ベンチプレス最後の記録 2026/11/10', '腕立て伏せ最後の記録 2026/11/01'])
    expect(screen.getByRole('region', { name: '有酸素' })).toBeInTheDocument()

    await user.click(within(chest).getByRole('link', { name: /ベンチプレス/ }))
    expect(router.state.location.pathname).toBe('/records/exercises/1')
  })

  test('まだ記録した種目がなければ、そう出す', async () => {
    vi.mocked(api.fetchUsedExercises).mockResolvedValue([])
    renderApp('/records/exercises')
    expect(await screen.findByText('まだ記録した種目がありません')).toBeInTheDocument()
  })

  test('記録の画面の「種目 ›」から開く', async () => {
    const user = userEvent.setup()
    vi.mocked(api.fetchWeeklyStats).mockRejectedValue(new api.ApiError(400, 'X', 'x'))
    vi.mocked(api.fetchCalendar).mockResolvedValue({ month: '2026-11', dates: [] })
    const { router } = renderApp('/records')

    await user.click(await screen.findByRole('link', { name: '種目 ›' }))
    expect(router.state.location.pathname).toBe('/records/exercises')
  })
})

describe('重量 × 回数の種目の推移', () => {
  test('直近3か月の推移と、自己ベスト・推定 1RM の最大を出す', async () => {
    renderApp('/records/exercises/1')

    expect(
      await screen.findByRole('heading', { level: 1, name: 'ベンチプレス' }),
    ).toBeInTheDocument()
    await waitFor(() => expect(api.fetchExerciseTrend).toHaveBeenCalledWith(1, '3m'))
    expect(screen.getByRole('tab', { name: '3か月' })).toHaveAttribute('aria-selected', 'true')

    const best = screen.getByRole('region', { name: 'ベスト' })
    expect(within(best).getByText('100kg（2026/10/20）')).toBeInTheDocument()
    expect(within(best).getByText('112.5kg（2026/11/02）')).toBeInTheDocument()
    expect(
      within(best).getByText('推定 1RM は推定値です（1〜10回のセットから Epley 式で計算）'),
    ).toBeInTheDocument()
  })

  test('グラフは最大重量と推定 1RM を切り替えられ、「表で見る」で同じ値を読める', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/exercises/1')

    const chart = await screen.findByRole('region', { name: '推移' })
    expect(await within(chart).findByTestId('line-chart')).toHaveTextContent('最大重量の推移')
    await user.click(within(chart).getByRole('button', { name: '表で見る' }))
    expect(rows(chart)).toEqual([
      ['2026/09/01', '85kg'],
      ['2026/11/02', '95kg'],
    ])

    await user.click(within(chart).getByRole('button', { name: '推定 1RM' }))
    expect(within(chart).getByRole('button', { name: '推定 1RM' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(router.state.location.search).toBe('?view=e1rm')
    expect(rows(chart)).toEqual([
      ['2026/09/01', '95.25kg'],
      ['2026/11/02', '112.5kg'],
    ])

    // 最初の値（最大重量）に戻すと、URL から view を外す
    await user.click(within(chart).getByRole('button', { name: '最大重量' }))
    expect(router.state.location.search).toBe('')
  })

  test('記録の一覧は新しい順で、押すとその記録の詳細へ移る', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/exercises/1')

    const list = await screen.findByRole('region', { name: '記録の一覧' })
    const links = within(list).getAllByRole('link')
    expect(links.map((link) => link.textContent)).toEqual([
      '2026/11/02最大 95kg × 3回・推定 1RM 112.5kg',
      '2026/09/01最大 85kg × 5回・推定 1RM 95.25kg',
    ])
    await user.click(links[0])
    expect(router.state.location.pathname).toBe('/workouts/104')
  })

  test('ポンド表示なら、重量をポンドで出す', async () => {
    startAs({ ...ME, weightUnit: 'lb' })
    renderApp('/records/exercises/1')

    const best = await screen.findByRole('region', { name: 'ベスト' })
    expect(within(best).getByText('220.5lb（2026/10/20）')).toBeInTheDocument()
  })
})

describe('期間と種目の切り替え', () => {
  test('1年・全期間のタブで期間を変え、記録がなければそう出す', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/exercises/1?view=e1rm')
    await screen.findByRole('region', { name: '推移' })

    await user.click(screen.getByRole('tab', { name: '1年' }))

    expect(screen.getByRole('tab', { name: '1年' })).toHaveAttribute('aria-selected', 'true')
    expect(router.state.location.search).toBe('?range=1y&view=e1rm')
    await waitFor(() => expect(api.fetchExerciseTrend).toHaveBeenCalledWith(1, '1y'))
    expect(
      await within(screen.getByRole('region', { name: '推移' })).findByText(
        'この期間の記録はありません',
      ),
    ).toBeInTheDocument()
    expect(
      within(screen.getByRole('region', { name: '記録の一覧' })).getByText(
        'この期間の記録はありません',
      ),
    ).toBeInTheDocument()

    await user.click(screen.getByRole('tab', { name: '全期間' }))
    await waitFor(() => expect(api.fetchExerciseTrend).toHaveBeenCalledWith(1, 'all'))

    // 3か月に戻すと、URL から range を外す
    await user.click(screen.getByRole('tab', { name: '3か月' }))
    expect(router.state.location.search).toBe('?view=e1rm')
  })

  test('「種目を変える」で記録した種目を出し、選ぶとその種目の推移へ移る（期間はそのまま）', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/exercises/1?range=1y')
    await screen.findByRole('heading', { level: 1, name: 'ベンチプレス' })
    expect(api.fetchUsedExercises).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: '種目を変える' }))
    expect(screen.getByRole('button', { name: '種目を変える' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
    await user.click(await screen.findByRole('link', { name: /腕立て伏せ/ }))

    expect(router.state.location.pathname).toBe('/records/exercises/8')
    expect(router.state.location.search).toBe('?range=1y')
    expect(await screen.findByRole('heading', { level: 1, name: '腕立て伏せ' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '種目を変える', expanded: true })).toBeNull()
  })
})

describe('種目の切り替え（読み込み済みの種目へ）', () => {
  test('前に見た種目へ移っても、種目の一覧は閉じる', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/records/exercises/8')
    await screen.findByRole('heading', { level: 1, name: '腕立て伏せ' })
    await router.navigate('/records/exercises/1')
    await screen.findByRole('heading', { level: 1, name: 'ベンチプレス' })

    await user.click(screen.getByRole('button', { name: '種目を変える' }))
    await user.click(await screen.findByRole('link', { name: /腕立て伏せ/ }))

    expect(await screen.findByRole('heading', { level: 1, name: '腕立て伏せ' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '種目を変える' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
  })
})

describe('記録方式ごとの表示', () => {
  test('ランニングは合計距離・合計時間・ペースを選べ、自己ベストは出さない', async () => {
    const user = userEvent.setup()
    renderApp('/records/exercises/48')

    const chart = await screen.findByRole('region', { name: '推移' })
    expect(
      within(chart)
        .getAllByRole('button', { pressed: false })
        .concat(within(chart).getAllByRole('button', { pressed: true }))
        .map((b) => b.textContent)
        .sort(),
    ).toEqual(['ペース', '合計時間', '合計距離'].sort())
    expect(screen.queryByRole('region', { name: 'ベスト' })).not.toBeInTheDocument()
    expect(
      within(screen.getByRole('region', { name: '記録の一覧' })).getByRole('link'),
    ).toHaveTextContent('2026/11/1430:00・5.00km・6:00/km')

    await user.click(within(chart).getByRole('button', { name: 'ペース' }))
    await user.click(within(chart).getByRole('button', { name: '表で見る' }))
    expect(rows(chart)).toEqual([['2026/11/14', '6:00/km']])
  })
})

describe('見つからないとき・読み込めないとき', () => {
  test('見えない・ない種目は「この種目は見つかりません」', async () => {
    renderApp('/records/exercises/999')
    expect(await screen.findByText('この種目は見つかりません')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '種目の一覧へ' })).toHaveAttribute(
      'href',
      '/records/exercises',
    )
  })

  test('種目の ID が数でなければ、通信せずに「この種目は見つかりません」', async () => {
    renderApp('/records/exercises/abc')
    expect(await screen.findByText('この種目は見つかりません')).toBeInTheDocument()
    expect(api.fetchExerciseTrend).not.toHaveBeenCalled()
  })

  test('読み込みに失敗したら、もう一度読み込める', async () => {
    const user = userEvent.setup()
    // 通信の失敗・5xx は自動でやり直すので、やり直さないエラー（4xx）で確かめる
    vi.mocked(api.fetchExerciseTrend).mockRejectedValueOnce(
      new api.ApiError(400, 'MALFORMED_REQUEST', '読み込めませんでした'),
    )
    renderApp('/records/exercises/1')

    await user.click(await screen.findByRole('button', { name: 'もう一度読み込む' }))
    expect(
      await screen.findByRole('heading', { level: 1, name: 'ベンチプレス' }),
    ).toBeInTheDocument()
  })
})

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
