import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { Exercise } from '../lib/api/types'
import { createQueryClient } from '../lib/queryClient'
import { ExercisePicker } from './ExercisePicker'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// 種目の選択（docs/screens.md S-13「種目の選択」）。最近使った種目・名前の検索・部位の絞り込み

const BENCH: Exercise = {
  id: 1,
  name: 'ベンチプレス',
  bodyPart: 'chest',
  recordType: 'weight_reps',
  isRunning: false,
  custom: false,
}
const PUSH_UP: Exercise = { ...BENCH, id: 8, name: '腕立て伏せ', recordType: 'reps' }
const SQUAT: Exercise = { ...BENCH, id: 16, name: 'スクワット', bodyPart: 'legs' }
const MINE: Exercise = {
  ...BENCH,
  id: 200,
  name: 'ブルガリアンスクワット',
  bodyPart: 'legs',
  custom: true,
}

const onSelect = vi.fn<(exercise: Exercise) => void>()
const onClose = vi.fn<() => void>()

function renderPicker(recordType?: Exercise['recordType']) {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <ExercisePicker onSelect={onSelect} onClose={onClose} recordType={recordType} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  onSelect.mockReset()
  onClose.mockReset()
  vi.mocked(api.fetchRecentExercises).mockResolvedValue([SQUAT])
  vi.mocked(api.searchExercises).mockResolvedValue([BENCH, PUSH_UP, SQUAT, MINE])
})

test('最近使った種目と、部位ごとの種目の一覧を出す。自分専用の種目には印を付ける', async () => {
  renderPicker()

  const recent = await screen.findByRole('region', { name: '最近使った種目' })
  expect(within(recent).getByRole('button', { name: /スクワット/ })).toHaveTextContent(
    'スクワット（脚）',
  )
  const chest = await screen.findByRole('region', { name: '胸' })
  expect(
    within(chest)
      .getAllByRole('button')
      .map((b) => b.textContent),
  ).toEqual(['ベンチプレス', '腕立て伏せ'])
  const legs = screen.getByRole('region', { name: '脚' })
  expect(within(legs).getByRole('button', { name: /ブルガリアンスクワット/ })).toHaveTextContent(
    '★自分専用',
  )
  expect(api.searchExercises).toHaveBeenCalledWith({ q: '', bodyPart: null })
})

test('名前で探すと、最近使った種目を隠して、検索の結果だけを出す', async () => {
  renderPicker()
  await screen.findByRole('region', { name: '最近使った種目' })
  vi.mocked(api.searchExercises).mockResolvedValue([SQUAT, MINE])

  await userEvent.type(screen.getByRole('searchbox', { name: '種目を検索' }), 'スクワット')

  expect(await screen.findByRole('button', { name: /ブルガリアンスクワット/ })).toBeInTheDocument()
  expect(api.searchExercises).toHaveBeenLastCalledWith({ q: 'スクワット', bodyPart: null })
  expect(screen.queryByRole('region', { name: '最近使った種目' })).not.toBeInTheDocument()
})

test('部位で絞り込む。「すべて」で戻す', async () => {
  renderPicker()
  await screen.findByRole('region', { name: '胸' })
  vi.mocked(api.searchExercises).mockResolvedValue([SQUAT, MINE])

  await userEvent.click(screen.getByRole('button', { name: '脚' }))

  expect(screen.getByRole('button', { name: '脚' })).toHaveAttribute('aria-pressed', 'true')
  expect(await screen.findByRole('region', { name: '脚' })).toBeInTheDocument()
  expect(screen.queryByRole('region', { name: '胸' })).not.toBeInTheDocument()
  expect(api.searchExercises).toHaveBeenLastCalledWith({ q: '', bodyPart: 'legs' })
  expect(screen.queryByRole('region', { name: '最近使った種目' })).not.toBeInTheDocument()

  vi.mocked(api.searchExercises).mockResolvedValue([BENCH, SQUAT])
  await userEvent.click(screen.getByRole('button', { name: 'すべて' }))
  expect(await screen.findByRole('region', { name: '胸' })).toBeInTheDocument()
  expect(api.searchExercises).toHaveBeenLastCalledWith({ q: '', bodyPart: null })
})

test('押すと、その種目を選ぶ', async () => {
  renderPicker()

  await userEvent.click(await screen.findByRole('button', { name: 'ベンチプレス' }))

  expect(onSelect).toHaveBeenCalledWith(BENCH)
})

test('見つからなければ、そう出す', async () => {
  vi.mocked(api.searchExercises).mockResolvedValue([])
  renderPicker()

  expect(await screen.findByText('見つかりませんでした')).toBeInTheDocument()
})

test('検索に失敗したら、理由を出す', async () => {
  vi.mocked(api.searchExercises).mockRejectedValue(
    new api.ApiError(400, 'VALIDATION_FAILED', '入力内容に誤りがあります'),
  )
  renderPicker()

  expect(await screen.findByText('入力内容に誤りがあります')).toBeInTheDocument()
})

test('← と Esc で閉じる', async () => {
  renderPicker()

  await userEvent.click(screen.getByRole('button', { name: '戻る' }))
  await userEvent.keyboard('{Escape}')

  expect(onClose).toHaveBeenCalledTimes(2)
})

test('開いたら、検索の欄に移る', () => {
  renderPicker()

  expect(screen.getByRole('searchbox', { name: '種目を検索' })).toHaveFocus()
})

test('種目を変えるときは、記録方式が同じ種目だけを出す（最近使った種目も）', async () => {
  vi.mocked(api.fetchRecentExercises).mockResolvedValue([PUSH_UP, SQUAT])
  renderPicker('weight_reps')

  expect(await screen.findByText('記録方式が同じ種目だけを出しています')).toBeInTheDocument()
  const recent = await screen.findByRole('region', { name: '最近使った種目' })
  expect(
    within(recent)
      .getAllByRole('button')
      .map((b) => b.textContent),
  ).toEqual(['スクワット（脚）'])
  const chest = screen.getByRole('region', { name: '胸' })
  expect(
    within(chest)
      .getAllByRole('button')
      .map((b) => b.textContent),
  ).toEqual(['ベンチプレス'])
})

test('記録方式が同じ種目が1つもなければ、見つからなかったと出す', async () => {
  vi.mocked(api.searchExercises).mockResolvedValue([BENCH])
  renderPicker('time_distance')

  expect(await screen.findByText('見つかりませんでした')).toBeInTheDocument()
})
