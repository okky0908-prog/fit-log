import { QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
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
const onUpdated = vi.fn<(exercise: Exercise) => void>()

function renderPicker(recordType?: Exercise['recordType']) {
  return render(
    <QueryClientProvider client={createQueryClient()}>
      <ExercisePicker
        onSelect={onSelect}
        onClose={onClose}
        onUpdated={onUpdated}
        recordType={recordType}
      />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  onSelect.mockReset()
  onClose.mockReset()
  onUpdated.mockReset()
  for (const fn of [api.createExercise, api.updateExercise, api.deleteExercise]) {
    vi.mocked(fn).mockReset()
  }
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
  expect(within(legs).getByRole('button', { name: /^ブルガリアンスクワット★/ })).toHaveTextContent(
    '★自分専用',
  )
  expect(api.searchExercises).toHaveBeenCalledWith({ q: '', bodyPart: null })
})

test('名前で探すと、最近使った種目を隠して、検索の結果だけを出す', async () => {
  renderPicker()
  await screen.findByRole('region', { name: '最近使った種目' })
  vi.mocked(api.searchExercises).mockResolvedValue([SQUAT, MINE])

  await userEvent.type(screen.getByRole('searchbox', { name: '種目を検索' }), 'スクワット')

  expect(
    await screen.findByRole('button', { name: /^ブルガリアンスクワット★/ }),
  ).toBeInTheDocument()
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

describe('自分専用の種目の追加', () => {
  const NEW: Exercise = { ...MINE, id: 201, name: 'ケーブルフライ', bodyPart: 'chest' }

  async function openForm(search = '') {
    renderPicker()
    await screen.findByRole('region', { name: '胸' })
    if (search) {
      await userEvent.type(screen.getByRole('searchbox', { name: '種目を検索' }), search)
    }
    await userEvent.click(screen.getByRole('button', { name: '＋ 自分専用の種目を追加' }))
    return screen.getByRole('dialog', { name: '自分専用の種目を追加' })
  }

  test('検索欄の文字を名前に入れて開き、追加したらそのまま選ぶ', async () => {
    vi.mocked(api.createExercise).mockResolvedValue(NEW)
    const form = await openForm('ケーブルフライ')

    expect(within(form).getByLabelText('種目の名前')).toHaveValue('ケーブルフライ')
    await userEvent.selectOptions(within(form).getByLabelText('部位'), '胸')
    await userEvent.click(within(form).getByRole('radio', { name: '重量 × 回数' }))
    await userEvent.click(within(form).getByRole('button', { name: '追加する' }))

    expect(api.createExercise).toHaveBeenCalledWith({
      name: 'ケーブルフライ',
      bodyPart: 'chest',
      recordType: 'weight_reps',
    })
    expect(onSelect).toHaveBeenCalledWith(NEW)
  })

  test('入力が足りなければ理由を出し、送らない', async () => {
    const form = await openForm()

    await userEvent.click(within(form).getByRole('button', { name: '追加する' }))

    expect(within(form).getByText('種目の名前は1〜30文字で入力してください')).toBeInTheDocument()
    expect(within(form).getByText('部位を選んでください')).toBeInTheDocument()
    expect(within(form).getByText('記録方式を選んでください')).toBeInTheDocument()
    expect(within(form).getByLabelText('種目の名前')).toHaveAttribute('aria-invalid', 'true')
    expect(api.createExercise).not.toHaveBeenCalled()
  })

  test('同じ名前がある（409）なら、名前の下に理由を出す', async () => {
    vi.mocked(api.createExercise).mockRejectedValue(
      new api.ApiError(409, 'EXERCISE_NAME_TAKEN', '同じ名前の種目があります', [
        { field: 'name', message: '同じ名前の種目があります' },
      ]),
    )
    const form = await openForm('ベンチプレス')
    await userEvent.selectOptions(within(form).getByLabelText('部位'), '胸')
    await userEvent.click(within(form).getByRole('radio', { name: '重量 × 回数' }))

    await userEvent.click(within(form).getByRole('button', { name: '追加する' }))

    expect(await within(form).findByText('同じ名前の種目があります')).toBeInTheDocument()
    expect(within(form).getByLabelText('種目の名前')).toHaveAttribute('aria-invalid', 'true')
    expect(onSelect).not.toHaveBeenCalled()
  })

  test('100件ある（409）なら、その理由を出す', async () => {
    vi.mocked(api.createExercise).mockRejectedValue(
      new api.ApiError(409, 'EXERCISE_LIMIT_EXCEEDED', '自分専用の種目は100件まで追加できます'),
    )
    const form = await openForm('新しい種目')
    await userEvent.selectOptions(within(form).getByLabelText('部位'), '脚')
    await userEvent.click(within(form).getByRole('radio', { name: '回数のみ' }))

    await userEvent.click(within(form).getByRole('button', { name: '追加する' }))

    expect(
      await within(form).findByText('自分専用の種目は100件まで追加できます'),
    ).toBeInTheDocument()
  })

  test('種目を変えるときは、記録方式を選ばせずに、同じ記録方式で追加する', async () => {
    vi.mocked(api.createExercise).mockResolvedValue(NEW)
    renderPicker('weight_reps')
    await screen.findByRole('region', { name: '胸' })
    await userEvent.click(screen.getByRole('button', { name: '＋ 自分専用の種目を追加' }))
    const form = screen.getByRole('dialog', { name: '自分専用の種目を追加' })

    expect(within(form).queryByRole('radio')).not.toBeInTheDocument()
    expect(within(form).getByText('記録方式：重量 × 回数')).toBeInTheDocument()
    await userEvent.type(within(form).getByLabelText('種目の名前'), 'ケーブルフライ')
    await userEvent.selectOptions(within(form).getByLabelText('部位'), '胸')
    await userEvent.click(within(form).getByRole('button', { name: '追加する' }))

    expect(api.createExercise).toHaveBeenCalledWith(
      expect.objectContaining({ recordType: 'weight_reps' }),
    )
  })

  test('キャンセルと Esc は、入力の画面だけを閉じる（種目の選択は開いたまま）', async () => {
    await openForm()

    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
    expect(screen.queryByRole('dialog', { name: '自分専用の種目を追加' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '＋ 自分専用の種目を追加' }))
    await userEvent.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: '自分専用の種目を追加' })).not.toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: '種目を選ぶ' })).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
    expect(api.createExercise).not.toHaveBeenCalled()
  })
})

describe('自分専用の種目の変更・削除', () => {
  test('メニューは自分専用の種目の行にだけ出し、右クリックでも開く', async () => {
    renderPicker()
    await screen.findByRole('region', { name: '胸' })

    expect(screen.queryByRole('button', { name: 'ベンチプレスのメニュー' })).not.toBeInTheDocument()
    await userEvent.pointer({
      keys: '[MouseRight]',
      target: screen.getByRole('button', { name: /^ブルガリアンスクワット★/ }),
    })

    expect(screen.getByRole('menuitem', { name: '名前・部位を変える' })).toBeInTheDocument()
    expect(screen.getByRole('menuitem', { name: '削除' })).toBeInTheDocument()
  })

  test('名前・部位を変えると、一覧を取り直し、変えたことを伝える', async () => {
    const renamed: Exercise = { ...MINE, name: 'スプリットスクワット', bodyPart: 'full_body' }
    vi.mocked(api.updateExercise).mockResolvedValue(renamed)
    renderPicker()
    await screen.findByRole('region', { name: '胸' })

    await userEvent.click(screen.getByRole('button', { name: 'ブルガリアンスクワットのメニュー' }))
    await userEvent.click(screen.getByRole('menuitem', { name: '名前・部位を変える' }))
    const form = screen.getByRole('dialog', { name: '種目の名前・部位を変える' })
    expect(within(form).getByLabelText('種目の名前')).toHaveValue('ブルガリアンスクワット')
    expect(within(form).getByLabelText('部位')).toHaveValue('legs')
    expect(within(form).getByText('記録方式は変えられません')).toBeInTheDocument()
    vi.mocked(api.searchExercises).mockClear()
    await userEvent.clear(within(form).getByLabelText('種目の名前'))
    await userEvent.type(within(form).getByLabelText('種目の名前'), 'スプリットスクワット')
    await userEvent.selectOptions(within(form).getByLabelText('部位'), '全身')
    await userEvent.click(within(form).getByRole('button', { name: '保存する' }))

    expect(api.updateExercise).toHaveBeenCalledWith(200, {
      name: 'スプリットスクワット',
      bodyPart: 'full_body',
    })
    expect(onUpdated).toHaveBeenCalledWith(renamed)
    expect(
      screen.queryByRole('dialog', { name: '種目の名前・部位を変える' }),
    ).not.toBeInTheDocument()
    await waitFor(() => expect(api.searchExercises).toHaveBeenCalled())
    expect(onSelect).not.toHaveBeenCalled()
  })

  test('削除は確かめてから行い、一覧を取り直す', async () => {
    vi.mocked(api.deleteExercise).mockResolvedValue(undefined)
    renderPicker()
    await screen.findByRole('region', { name: '胸' })

    await userEvent.click(screen.getByRole('button', { name: 'ブルガリアンスクワットのメニュー' }))
    await userEvent.click(screen.getByRole('menuitem', { name: '削除' }))
    vi.mocked(api.searchExercises).mockClear()
    await userEvent.click(
      within(
        screen.getByRole('dialog', { name: '「ブルガリアンスクワット」を削除しますか？' }),
      ).getByRole('button', { name: '削除する' }),
    )

    expect(api.deleteExercise).toHaveBeenCalledWith(200)
    await waitFor(() => expect(api.searchExercises).toHaveBeenCalled())
  })

  test('記録で使っている種目は消せないので、その理由を出す', async () => {
    vi.mocked(api.deleteExercise).mockRejectedValue(
      new api.ApiError(409, 'EXERCISE_IN_USE', '記録で使っている種目は削除できません'),
    )
    renderPicker()
    await screen.findByRole('region', { name: '胸' })

    await userEvent.click(screen.getByRole('button', { name: 'ブルガリアンスクワットのメニュー' }))
    await userEvent.click(screen.getByRole('menuitem', { name: '削除' }))
    await userEvent.click(screen.getByRole('button', { name: '削除する' }))

    expect(await screen.findByText('記録で使っている種目は削除できません')).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: /を削除しますか/ })).not.toBeInTheDocument()
  })
})
