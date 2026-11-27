import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { BodyRecord } from '../lib/api/types'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)
// Recharts は jsdom では大きさが測れず描けないので、グラフの部品は偽物にする（値は「表で見る」の表で確かめる）
vi.mock('../components/BodyLineChart', () => ({
  default: () => <div data-testid="body-chart" />,
}))

// S-25 体重の記録（docs/screens.md S-25、F-90〜F-92）。通信の関数が呼ばれたかは waitFor で待って確かめる

/** 日本時間 2026-11-14（土） */
const NOW = new Date('2026-11-14T10:00:00+09:00')

const RECORDS: BodyRecord[] = [
  { recordedOn: '2026-11-13', weightKg: 73.4, bodyFatPct: 18.7 },
  { recordedOn: '2026-11-12', weightKg: 73.6, bodyFatPct: null },
]

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  vi.mocked(api.fetchBodyRecords).mockReset()
  vi.mocked(api.fetchBodyRecords).mockResolvedValue({
    items: RECORDS,
    nextCursor: null,
    hasNext: false,
  })
  vi.mocked(api.fetchBodyTrend).mockReset()
  vi.mocked(api.fetchBodyTrend).mockImplementation(async (range) => ({
    points:
      range === '3m'
        ? [
            { date: '2026-11-12', weightKg: 73.6, bodyFatPct: null },
            { date: '2026-11-13', weightKg: 73.4, bodyFatPct: 18 },
          ]
        : [],
  }))
  vi.mocked(api.putBodyRecord).mockReset()
  vi.mocked(api.putBodyRecord).mockImplementation(async (date, input) => ({
    recordedOn: date,
    weightKg: input.weight,
    bodyFatPct: input.bodyFatPct,
  }))
  vi.mocked(api.deleteBodyRecord).mockReset()
  vi.mocked(api.deleteBodyRecord).mockResolvedValue(undefined)
  startAs(ME)
})

afterEach(() => {
  vi.useRealTimers()
})

describe('記録する', () => {
  test('今日の日付で体重と体脂肪率を記録し、お知らせを出して一覧を読み直す', async () => {
    const user = userEvent.setup()
    renderApp('/body')

    expect(await screen.findByRole('heading', { level: 1, name: '体重の記録' })).toBeInTheDocument()
    expect(screen.getByText('🔒 自分だけが見られます')).toBeInTheDocument()
    expect(screen.getByLabelText('日付')).toHaveValue('2026-11-14')
    await user.type(screen.getByLabelText('体重（kg）'), '73.0')
    await user.type(screen.getByLabelText('体脂肪率（%・任意）'), '18.5')
    await user.click(screen.getByRole('button', { name: '記録する' }))

    await waitFor(() =>
      expect(api.putBodyRecord).toHaveBeenCalledWith('2026-11-14', {
        weight: 73,
        weightUnit: 'kg',
        bodyFatPct: 18.5,
      }),
    )
    expect(await screen.findByText('体重を記録しました')).toBeInTheDocument()
    await waitFor(() => expect(api.fetchBodyRecords).toHaveBeenCalledTimes(2))
  })

  test('同じ日付の記録があれば、今の値を入れてボタンを「上書きする」にする', async () => {
    const user = userEvent.setup()
    renderApp('/body')
    await screen.findByRole('region', { name: '記録の一覧' })

    const date = screen.getByLabelText('日付')
    await user.clear(date)
    await user.type(date, '2026-11-13')

    expect(screen.getByLabelText('体重（kg）')).toHaveValue('73.4')
    expect(screen.getByLabelText('体脂肪率（%・任意）')).toHaveValue('18.7')
    await user.click(screen.getByRole('button', { name: '上書きする' }))
    expect(await screen.findByText('体重を上書きしました')).toBeInTheDocument()

    await user.clear(date)
    await user.type(date, '2026-11-10')
    expect(screen.getByRole('button', { name: '記録する' })).toBeInTheDocument()

    // 値を書き換えてから別の日に移り、戻ると、その日の記録の値を入れ直す
    await user.clear(date)
    await user.type(date, '2026-11-13')
    await user.clear(screen.getByLabelText('体重（kg）'))
    await user.type(screen.getByLabelText('体重（kg）'), '70.0')
    await user.clear(date)
    await user.type(date, '2026-11-10')
    await user.clear(date)
    await user.type(date, '2026-11-13')
    expect(screen.getByLabelText('体重（kg）')).toHaveValue('73.4')
  })

  test('ポンド表示なら、ポンドで入力して送り、一覧もポンドで出す', async () => {
    const user = userEvent.setup()
    startAs({ ...ME, weightUnit: 'lb' })
    renderApp('/body')

    const list = await screen.findByRole('region', { name: '記録の一覧' })
    expect(await within(list).findByText('161.8lb')).toBeInTheDocument()
    await user.type(screen.getByLabelText('体重（lb）'), '150.2')
    await user.click(screen.getByRole('button', { name: '記録する' }))

    await waitFor(() =>
      expect(api.putBodyRecord).toHaveBeenCalledWith('2026-11-14', {
        weight: 150.2,
        weightUnit: 'lb',
        bodyFatPct: null,
      }),
    )
  })

  test('範囲・刻みに合わない値と未来の日付は、送らずに項目の下に誤りを出す', async () => {
    const user = userEvent.setup()
    renderApp('/body')
    await screen.findByRole('region', { name: '記録の一覧' })

    const date = screen.getByLabelText('日付')
    await user.clear(date)
    await user.type(date, '2026-11-15')
    await user.type(screen.getByLabelText('体重（kg）'), '19.9')
    await user.type(screen.getByLabelText('体脂肪率（%・任意）'), '75.1')
    await user.click(screen.getByRole('button', { name: '記録する' }))

    expect(screen.getByText('未来の日付は選べません')).toBeInTheDocument()
    expect(screen.getByLabelText('体重（kg）')).toHaveAccessibleDescription(
      '体重は20.0〜300.0kgの範囲で、0.1kg刻みで入力してください',
    )
    expect(
      screen.getByText('体脂肪率は1.0〜75.0%の範囲で、0.1%刻みで入力してください'),
    ).toBeInTheDocument()
    expect(api.putBodyRecord).not.toHaveBeenCalled()
  })

  test('サーバーの項目の誤りは項目の下に、通信の失敗はお知らせに出し、入力した値は消さない', async () => {
    const user = userEvent.setup()
    vi.mocked(api.putBodyRecord)
      .mockRejectedValueOnce(
        new api.ApiError(400, 'VALIDATION_FAILED', '入力内容に誤りがあります', [
          { field: 'weight', message: 'サーバーの誤り' },
        ]),
      )
      .mockRejectedValueOnce(
        new api.ApiError(0, 'NETWORK_ERROR', '通信に失敗しました。もう一度お試しください'),
      )
    renderApp('/body')
    await screen.findByRole('region', { name: '記録の一覧' })

    await user.type(screen.getByLabelText('体重（kg）'), '73.0')
    await user.click(screen.getByRole('button', { name: '記録する' }))
    expect(await screen.findByText('サーバーの誤り')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '記録する' }))
    expect(
      await screen.findByText('通信に失敗しました。もう一度お試しください'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('体重（kg）')).toHaveValue('73.0')
  })
})

describe('一覧と削除', () => {
  test('日付の新しい順に出し、確認のあとに削除してお知らせを出す', async () => {
    const user = userEvent.setup()
    renderApp('/body')

    const list = await screen.findByRole('region', { name: '記録の一覧' })
    const rows = await within(list).findAllByRole('listitem')
    expect(rows.map((row) => row.textContent)).toEqual([
      '2026/11/1373.4kg18.7%削除',
      '2026/11/1273.6kg—削除',
    ])

    await user.click(within(rows[0]).getByRole('button', { name: '2026/11/13 の記録を削除' }))
    const dialog = screen.getByRole('dialog', { name: '2026/11/13 の体重の記録を削除しますか？' })
    expect(api.deleteBodyRecord).not.toHaveBeenCalled()
    await user.click(within(dialog).getByRole('button', { name: '削除する' }))

    await waitFor(() => expect(api.deleteBodyRecord).toHaveBeenCalledWith('2026-11-13'))
    expect(await screen.findByText('体重の記録を削除しました')).toBeInTheDocument()
    // 一覧を読み直す
    await waitFor(() => expect(api.fetchBodyRecords).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  test('「さらに表示」で続きを読む', async () => {
    const user = userEvent.setup()
    vi.mocked(api.fetchBodyRecords).mockImplementation(async (cursor) =>
      cursor === undefined
        ? { items: [RECORDS[0]], nextCursor: 'next', hasNext: true }
        : { items: [RECORDS[1]], nextCursor: null, hasNext: false },
    )
    renderApp('/body')

    const list = await screen.findByRole('region', { name: '記録の一覧' })
    await user.click(await within(list).findByRole('button', { name: 'さらに表示' }))

    await waitFor(() => expect(api.fetchBodyRecords).toHaveBeenCalledWith('next'))
    expect(await within(list).findByText('2026/11/12')).toBeInTheDocument()
    expect(within(list).queryByRole('button', { name: 'さらに表示' })).not.toBeInTheDocument()
  })

  test('記録がなければ、そう出す', async () => {
    vi.mocked(api.fetchBodyRecords).mockResolvedValue({
      items: [],
      nextCursor: null,
      hasNext: false,
    })
    renderApp('/body')
    expect(await screen.findByText('まだ体重の記録がありません')).toBeInTheDocument()
  })
})

describe('推移', () => {
  test('直近3か月の体重と体脂肪率を「表で見る」で読め、1年に切り替えられる', async () => {
    const user = userEvent.setup()
    const { router } = renderApp('/body')

    const chart = await screen.findByRole('region', { name: '推移' })
    expect(within(chart).getByRole('tab', { name: '3か月' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await waitFor(() => expect(api.fetchBodyTrend).toHaveBeenCalledWith('3m'))
    expect(await within(chart).findByTestId('body-chart')).toBeInTheDocument()
    await user.click(within(chart).getByRole('button', { name: '表で見る' }))
    const rows = within(within(chart).getByRole('table')).getAllByRole('row').slice(1)
    expect(rows.map((row) => row.textContent)).toEqual([
      '2026/11/1273.6kg—',
      '2026/11/1373.4kg18.0%',
    ])

    await user.click(within(chart).getByRole('tab', { name: '1年' }))
    expect(router.state.location.search).toBe('?range=1y')
    await waitFor(() => expect(api.fetchBodyTrend).toHaveBeenCalledWith('1y'))
    expect(await within(chart).findByText('この期間の記録はありません')).toBeInTheDocument()
  })
})

describe('ほかの画面との行き来', () => {
  test('アイコンのメニューの「体重の記録」から開く', async () => {
    const user = userEvent.setup()
    vi.mocked(api.fetchUsedExercises).mockResolvedValue([])
    const { router } = renderApp('/records/exercises')

    await user.click(await screen.findByRole('button', { name: '自分のメニュー' }))
    await user.click(screen.getByRole('menuitem', { name: '体重の記録' }))

    expect(router.state.location.pathname).toBe('/body')
    expect(await screen.findByRole('heading', { level: 1, name: '体重の記録' })).toBeInTheDocument()
  })
})
