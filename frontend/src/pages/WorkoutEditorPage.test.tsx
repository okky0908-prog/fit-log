import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import type { Exercise, Workout } from '../lib/api/types'
import { shrinkImage, UnusableImageError } from '../lib/image/shrink'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)
// 画像の縮小は Canvas を使うので jsdom では動かない。縮小の決まりは lib/image/shrink.test.ts で確かめる
vi.mock('../lib/image/shrink', async (original) => ({
  ...(await original<typeof import('../lib/image/shrink')>()),
  shrinkImage: vi.fn<typeof import('../lib/image/shrink').shrinkImage>(),
}))

// S-13 記録の作成・編集（docs/screens.md）。画像・書きかけの一時保存・自分専用の種目の追加は 9-3 で足す

const NOW = new Date('2026-11-22T10:00:00+09:00')

const BENCH: Exercise = {
  id: 1,
  name: 'ベンチプレス',
  bodyPart: 'chest',
  recordType: 'weight_reps',
  isRunning: false,
  custom: false,
}
const PUSH_UP: Exercise = { ...BENCH, id: 8, name: '腕立て伏せ', recordType: 'reps' }
const RUNNING: Exercise = {
  ...BENCH,
  id: 48,
  name: 'ランニング',
  bodyPart: 'cardio',
  recordType: 'time_distance',
  isRunning: true,
}

const SAVED: Workout = {
  id: 5,
  author: { username: ME.username, displayName: ME.displayName, iconUrl: null, isPrivate: false },
  performedOn: '2026-11-22',
  createdAt: '2026-11-22T10:00:00+09:00',
  editedAt: null,
  body: '',
  durationMin: null,
  visibility: 'public',
  exercises: [
    {
      exerciseId: 1,
      name: 'ベンチプレス',
      bodyPart: 'chest',
      recordType: 'weight_reps',
      isRunning: false,
      personalBest: false,
      sets: [{ weightKg: 60, reps: 10, durationSec: null, distanceKm: null, personalBest: false }],
    },
  ],
  images: [],
  likeCount: 0,
  likedByMe: false,
  commentCount: 0,
  mine: true,
}

beforeEach(() => {
  vi.useFakeTimers({ toFake: ['Date'] })
  vi.setSystemTime(NOW)
  for (const fn of [api.createWorkout, api.updateWorkout, api.fetchWorkout]) {
    vi.mocked(fn).mockReset()
  }
  vi.mocked(api.fetchRecentExercises).mockResolvedValue([])
  vi.mocked(api.searchExercises).mockResolvedValue([BENCH, PUSH_UP, RUNNING])
  vi.mocked(api.fetchWorkout).mockResolvedValue(SAVED)
  // 縮小したものとして、元の名前を中身に入れた JPEG を返す（どの画像を送ったかを確かめるため）
  vi.mocked(shrinkImage).mockReset()
  vi.mocked(shrinkImage).mockImplementation(
    async (file) => new Blob([`shrunk:${file.name}`], { type: 'image/jpeg' }),
  )
  localStorage.clear()
  startAs(ME)
})

afterEach(() => {
  vi.useRealTimers()
})

async function addExercise(name: string) {
  await userEvent.click(screen.getByRole('button', { name: '＋ 種目を追加' }))
  const picker = await screen.findByRole('dialog', { name: '種目を選ぶ' })
  await userEvent.click(await within(picker).findByRole('button', { name }))
}

async function fill(label: string, value: string) {
  const input = screen.getByLabelText(label)
  await userEvent.clear(input)
  await userEvent.type(input, value)
}

describe('作成', () => {
  test('初めは、実施日が今日・公開で、種目がないので投稿できない', async () => {
    renderApp('/workouts/new')

    expect(await screen.findByRole('heading', { name: '新しい記録' })).toBeInTheDocument()
    expect(screen.getByLabelText('実施日')).toHaveValue('2026-11-22')
    expect(screen.getByLabelText('実施日')).toHaveAttribute('max', '2026-11-22')
    expect(screen.getByRole('radio', { name: /公開/ })).toBeChecked()
    expect(screen.getByRole('button', { name: '投稿する' })).toBeDisabled()
  })

  test('種目とセットを入れて投稿すると、詳細へ移ってお知らせを出す', async () => {
    vi.mocked(api.createWorkout).mockResolvedValue(SAVED)
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })

    await addExercise('ベンチプレス')
    expect(screen.queryByRole('dialog', { name: '種目を選ぶ' })).not.toBeInTheDocument()
    await fill('1セット目の重量（kg）', '60')
    await fill('1セット目の回数', '10')
    // 「＋ セットを追加」は、直前のセットと同じ値を入れる
    await userEvent.click(screen.getByRole('button', { name: '＋ セットを追加' }))
    expect(screen.getByLabelText('2セット目の重量（kg）')).toHaveValue('60')
    expect(screen.getByLabelText('2セット目の回数')).toHaveValue('10')
    await fill('2セット目の回数', '8')
    await addExercise('ランニング')
    await fill('1セット目の時間', '30:00')
    await fill('1セット目の距離（km）', '5')
    await fill('トレーニング時間（分）', '75')
    await fill('ひとこと', '胸の日')
    await userEvent.click(screen.getByRole('radio', { name: /フォロワー限定/ }))

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    expect(await screen.findByText('記録を投稿しました')).toBeInTheDocument()
    expect(api.createWorkout).toHaveBeenCalledWith(
      {
        performedOn: '2026-11-22',
        body: '胸の日',
        durationMin: 75,
        visibility: 'followers',
        weightUnit: 'kg',
        distanceUnit: 'km',
        exercises: [
          {
            exerciseId: 1,
            sets: [
              { weight: 60, reps: 10 },
              { weight: 60, reps: 8 },
            ],
          },
          { exerciseId: 48, sets: [{ durationSec: 1800, distance: 5 }] },
        ],
      },
      [],
    )
    // 詳細の画面（作成の画面は履歴に残さない）
    expect(await screen.findByRole('region', { name: 'ベンチプレス（胸）' })).toBeInTheDocument()
  })

  test('自己ベストを更新した種目があれば、お知らせで伝える', async () => {
    vi.mocked(api.createWorkout).mockResolvedValue({
      ...SAVED,
      exercises: [{ ...SAVED.exercises[0], personalBest: true }],
    })
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('腕立て伏せ')
    await fill('1セット目の回数', '20')

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    expect(
      await screen.findByText('🏆 ベンチプレスの自己ベストを更新しました！'),
    ).toBeInTheDocument()
  })

  test('重量の単位が lb なら、lb で入力して送る', async () => {
    startAs({ ...ME, weightUnit: 'lb', distanceUnit: 'mi' })
    vi.mocked(api.createWorkout).mockResolvedValue(SAVED)
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('ベンチプレス')
    await fill('1セット目の重量（lb）', '135.5')
    await fill('1セット目の回数', '5')

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    await screen.findByText('記録を投稿しました')
    expect(api.createWorkout).toHaveBeenCalledWith(
      expect.objectContaining({
        weightUnit: 'lb',
        distanceUnit: 'mi',
        exercises: [{ exerciseId: 1, sets: [{ weight: 135.5, reps: 5 }] }],
      }),
      [],
    )
  })

  test('範囲・刻みの外の値は、欄を離れたときに理由を出し、投稿できない', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('ベンチプレス')

    await fill('1セット目の重量（kg）', '60.1')
    await fill('1セット目の回数', '10')

    expect(
      screen.getByText('重量は0.25〜500kgの範囲で、0.25kg刻みで入力してください'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('1セット目の重量（kg）')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('button', { name: '投稿する' })).toBeDisabled()

    await fill('1セット目の重量（kg）', '60.25')
    expect(screen.getByRole('button', { name: '投稿する' })).toBeEnabled()
  })

  test('サーバーの入力のエラーは、その欄の下に出す', async () => {
    vi.mocked(api.createWorkout).mockRejectedValue(
      new api.ApiError(400, 'VALIDATION_FAILED', '入力内容に誤りがあります', [
        { field: 'exercises[0].sets[0].reps', message: 'サーバーの回数のエラー' },
        { field: 'performedOn', message: 'サーバーの実施日のエラー' },
      ]),
    )
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('腕立て伏せ')
    await fill('1セット目の回数', '20')

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    expect(await screen.findByText('サーバーの回数のエラー')).toBeInTheDocument()
    expect(screen.getByText('サーバーの実施日のエラー')).toBeInTheDocument()
    expect(screen.getByLabelText('1セット目の回数')).toHaveValue('20')
  })

  test('通信に失敗したら、お知らせで伝え、入力は残す', async () => {
    vi.mocked(api.createWorkout).mockRejectedValue(
      new api.ApiError(0, 'NETWORK_ERROR', api.NETWORK_ERROR_MESSAGE),
    )
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('腕立て伏せ')
    await fill('1セット目の回数', '20')

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    expect(await screen.findByText(api.NETWORK_ERROR_MESSAGE)).toBeInTheDocument()
    expect(screen.getByLabelText('1セット目の回数')).toHaveValue('20')
    expect(screen.getByRole('button', { name: '投稿する' })).toBeEnabled()
  })
})

describe('種目とセットの操作', () => {
  test('種目を足した直後と、セットを足した直後は、まだ入力していない欄にエラーを出さない', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })

    await addExercise('ベンチプレス')
    await addExercise('腕立て伏せ')
    await userEvent.click(screen.getAllByRole('button', { name: '＋ セットを追加' })[0])

    expect(screen.queryByText(/で入力してください$/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('1セット目の重量（kg）')).not.toHaveAttribute('aria-invalid')
  })

  test('種目は ↑ ↓ で並べ替え、削除できる', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('ベンチプレス')
    await addExercise('腕立て伏せ')
    const names = () =>
      screen.getAllByRole('region', { name: /（胸）$/ }).map((r) => r.getAttribute('aria-label'))
    expect(names()).toEqual(['ベンチプレス（胸）', '腕立て伏せ（胸）'])
    // 一番上の「上へ」と、一番下の「下へ」は押せない
    const first = screen.getByRole('region', { name: 'ベンチプレス（胸）' })
    expect(within(first).getByRole('button', { name: '上へ' })).toBeDisabled()

    await userEvent.click(within(first).getByRole('button', { name: '下へ' }))
    expect(names()).toEqual(['腕立て伏せ（胸）', 'ベンチプレス（胸）'])

    await userEvent.click(
      within(screen.getByRole('region', { name: '腕立て伏せ（胸）' })).getByRole('button', {
        name: '種目を削除',
      }),
    )
    expect(names()).toEqual(['ベンチプレス（胸）'])
  })

  test('セットは削除できる。最後の1つも消せて、そのときは投稿できない', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('腕立て伏せ')
    await fill('1セット目の回数', '20')
    await userEvent.click(screen.getByRole('button', { name: '＋ セットを追加' }))
    await fill('2セット目の回数', '15')

    await userEvent.click(screen.getByRole('button', { name: '1セット目を削除' }))
    expect(screen.getByLabelText('1セット目の回数')).toHaveValue('15')
    await userEvent.click(screen.getByRole('button', { name: '1セット目を削除' }))

    expect(screen.queryByLabelText('1セット目の回数')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '投稿する' })).toBeDisabled()
    // 消したあとの最初のセットは空で足す
    await userEvent.click(screen.getByRole('button', { name: '＋ セットを追加' }))
    expect(screen.getByLabelText('1セット目の回数')).toHaveValue('')
  })

  test('ひとことの文字数を出し、280文字を超えたら理由を出す', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })

    await fill('ひとこと', '胸の日💪')
    expect(screen.getByText('4 / 280')).toBeInTheDocument()

    await userEvent.click(screen.getByLabelText('ひとこと'))
    await userEvent.paste('あ'.repeat(280))
    await userEvent.tab()
    expect(screen.getByText('284 / 280')).toBeInTheDocument()
    expect(screen.getByText('ひとことは280文字以内で入力してください')).toBeInTheDocument()
  })
})

describe('閉じる', () => {
  test('何も入力していなければ、確かめずに前の画面へ戻る', async () => {
    renderApp('/workouts/new')

    await userEvent.click(await screen.findByRole('button', { name: '閉じる' }))

    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
  })

  test('入力中なら確かめる。キャンセルなら残る', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await fill('ひとこと', '書きかけ')

    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))
    const dialog = screen.getByRole('dialog', {
      name: '入力中の記録は保存されません。閉じますか？',
    })
    await userEvent.click(within(dialog).getByRole('button', { name: 'キャンセル' }))
    expect(screen.getByLabelText('ひとこと')).toHaveValue('書きかけ')

    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))
    await userEvent.click(screen.getByRole('button', { name: '閉じる（入力を捨てる）' }))
    expect(await screen.findByRole('heading', { name: 'ホーム' })).toBeInTheDocument()
  })
})

describe('編集', () => {
  const EXISTING: Workout = {
    ...SAVED,
    id: 12,
    body: '前のひとこと',
    durationMin: 60,
    visibility: 'private',
    images: [
      { id: 3, url: 'https://example.com/3.jpg', sortOrder: 1 },
      { id: 1, url: 'https://example.com/1.jpg', sortOrder: 2 },
    ],
  }

  test('今の内容が入った状態で開き、保存すると画像を残したまま送る', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValue(EXISTING)
    vi.mocked(api.updateWorkout).mockResolvedValue({ ...EXISTING, body: '後のひとこと' })
    renderApp('/workouts/12/edit')

    expect(await screen.findByRole('heading', { name: '記録を編集' })).toBeInTheDocument()
    expect(screen.getByLabelText('ひとこと')).toHaveValue('前のひとこと')
    expect(screen.getByLabelText('トレーニング時間（分）')).toHaveValue('60')
    expect(screen.getByRole('radio', { name: /自分のみ/ })).toBeChecked()
    expect(screen.getByLabelText('1セット目の重量（kg）')).toHaveValue('60')
    await fill('ひとこと', '後のひとこと')

    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(await screen.findByText('記録を保存しました')).toBeInTheDocument()
    expect(api.updateWorkout).toHaveBeenCalledWith(
      12,
      {
        performedOn: '2026-11-22',
        body: '後のひとこと',
        durationMin: 60,
        visibility: 'private',
        weightUnit: 'kg',
        distanceUnit: 'km',
        exercises: [{ exerciseId: 1, sets: [{ weight: 60, reps: 10 }] }],
        keepImageIds: [3, 1],
      },
      [],
    )
  })

  test('他人の記録・見られない記録は「この記録は見つかりません」', async () => {
    vi.mocked(api.fetchWorkout).mockResolvedValueOnce({ ...EXISTING, mine: false })
    renderApp('/workouts/12/edit')

    expect(await screen.findByText('この記録は見つかりません')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '保存する' })).not.toBeInTheDocument()
  })
})

describe('画像', () => {
  const photo = (name: string) => new File(['x'], name, { type: 'image/jpeg' })

  async function readyToPost() {
    await addExercise('腕立て伏せ')
    await fill('1セット目の回数', '20')
  }

  async function sentNames(images: Blob[]): Promise<string[]> {
    return Promise.all(images.map(async (image) => (await image.text()).replace('shrunk:', '')))
  }

  test('選ぶと縮小してプレビューを出し、選んだ順に送る。× で外せる', async () => {
    vi.mocked(api.createWorkout).mockResolvedValue(SAVED)
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await readyToPost()

    await userEvent.upload(screen.getByLabelText('画像を追加'), [photo('a.jpg'), photo('b.jpg')])
    await userEvent.upload(screen.getByLabelText('画像を追加'), photo('c.jpg'))

    expect(await screen.findByAltText('画像 3')).toBeInTheDocument()
    expect(shrinkImage).toHaveBeenCalledTimes(3)
    await userEvent.click(screen.getByRole('button', { name: '画像 2 を外す' }))
    expect(screen.queryByAltText('画像 3')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    await screen.findByText('記録を投稿しました')
    const [, images] = vi.mocked(api.createWorkout).mock.calls[0]
    expect(await sentNames(images ?? [])).toEqual(['a.jpg', 'c.jpg'])
  })

  test('4枚まで。超えた分は足さずに理由を出し、4枚なら「＋」を出さない', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })

    await userEvent.upload(
      screen.getByLabelText('画像を追加'),
      ['1', '2', '3', '4', '5'].map((n) => photo(`${n}.jpg`)),
    )

    expect(await screen.findByAltText('画像 4')).toBeInTheDocument()
    expect(screen.queryByAltText('画像 5')).not.toBeInTheDocument()
    expect(screen.getByText('画像は4枚まで選べます')).toBeInTheDocument()
    expect(screen.queryByLabelText('画像を追加')).not.toBeInTheDocument()
    expect(shrinkImage).toHaveBeenCalledTimes(4)
  })

  test('すでにある枚数と合わせて4枚まで（3枚のところに2枚選ぶと、1枚だけ足す）', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await userEvent.upload(
      screen.getByLabelText('画像を追加'),
      ['1', '2', '3'].map((n) => photo(`${n}.jpg`)),
    )
    await screen.findByAltText('画像 3')

    await userEvent.upload(screen.getByLabelText('画像を追加'), [photo('4.jpg'), photo('5.jpg')])

    expect(await screen.findByAltText('画像 4')).toBeInTheDocument()
    expect(screen.queryByAltText('画像 5')).not.toBeInTheDocument()
    expect(screen.getByText('画像は4枚まで選べます')).toBeInTheDocument()
    expect(shrinkImage).toHaveBeenCalledTimes(4)
  })

  test('使えない画像は足さずに理由を出す。ほかの画像は足す', async () => {
    vi.mocked(shrinkImage).mockImplementation(async (file) => {
      if (file.name === 'bad.gif') throw new UnusableImageError()
      return new Blob(['ok'], { type: 'image/jpeg' })
    })
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })

    await userEvent.upload(screen.getByLabelText('画像を追加'), [
      photo('good.jpg'),
      new File(['x'], 'bad.gif', { type: 'image/jpeg' }),
    ])

    expect(
      await screen.findByText('この画像は使えません（JPEG・PNG・WebP、20MBまで）'),
    ).toBeInTheDocument()
    expect(screen.getByAltText('画像 1')).toBeInTheDocument()
    expect(screen.queryByAltText('画像 2')).not.toBeInTheDocument()
  })

  test('サーバーが画像を受け付けなかったら、画像の欄の下に理由を出す', async () => {
    vi.mocked(api.createWorkout).mockRejectedValue(
      new api.ApiError(400, 'VALIDATION_FAILED', '入力内容に誤りがあります', [
        { field: 'images[0]', message: '位置情報を含む画像はアップロードできません' },
      ]),
    )
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await readyToPost()
    await userEvent.upload(screen.getByLabelText('画像を追加'), photo('a.jpg'))
    await screen.findByAltText('画像 1')

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    expect(
      await screen.findByText('画像 1：位置情報を含む画像はアップロードできません'),
    ).toBeInTheDocument()
  })

  test('画像だけを選んだときも、閉じる前に確かめる', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await userEvent.upload(screen.getByLabelText('画像を追加'), photo('a.jpg'))
    await screen.findByAltText('画像 1')

    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))

    expect(
      screen.getByRole('dialog', { name: '入力中の記録は保存されません。閉じますか？' }),
    ).toBeInTheDocument()
  })

  test('編集では今の画像を出し、外した画像は残さず、新しい画像を後ろに足して送る', async () => {
    const existing: Workout = {
      ...SAVED,
      id: 12,
      images: [
        { id: 3, url: 'https://example.com/3.jpg', sortOrder: 1 },
        { id: 1, url: 'https://example.com/1.jpg', sortOrder: 2 },
        { id: 7, url: 'https://example.com/7.jpg', sortOrder: 3 },
      ],
    }
    vi.mocked(api.fetchWorkout).mockResolvedValue(existing)
    vi.mocked(api.updateWorkout).mockResolvedValue(existing)
    renderApp('/workouts/12/edit')

    expect(await screen.findByAltText('画像 1')).toHaveAttribute('src', 'https://example.com/3.jpg')
    await userEvent.click(screen.getByRole('button', { name: '画像 2 を外す' }))
    await userEvent.upload(screen.getByLabelText('画像を追加'), photo('new.jpg'))
    expect(await screen.findByAltText('画像 3')).toHaveAttribute(
      'src',
      expect.stringMatching(/^blob:/),
    )

    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    await screen.findByText('記録を保存しました')
    const [id, input, images] = vi.mocked(api.updateWorkout).mock.calls[0]
    expect(id).toBe(12)
    expect(input.keepImageIds).toEqual([3, 7])
    expect(await sentNames(images ?? [])).toEqual(['new.jpg'])
  })
})

describe('書きかけの一時保存', () => {
  const KEY = `fitlog:workout-draft:${ME.username}`

  async function writeSomething() {
    await addExercise('ベンチプレス')
    await fill('1セット目の重量（kg）', '60')
    await fill('1セット目の回数', '10')
    await fill('ひとこと', '書きかけ')
  }

  test('入力のたびに保存し、次に開いたとき「続ける」で戻せる。画像は選び直すと伝える', async () => {
    const first = renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await writeSomething()
    expect(JSON.parse(localStorage.getItem(KEY) ?? 'null')).toMatchObject({ body: '書きかけ' })
    first.unmount()

    renderApp('/workouts/new')

    expect(
      await screen.findByText('書きかけの記録があります。続きから入力しますか？'),
    ).toBeInTheDocument()
    expect(screen.getByText('画像は選び直してください')).toBeInTheDocument()
    expect(screen.getByLabelText('ひとこと')).toHaveValue('')
    await userEvent.click(screen.getByRole('button', { name: '続ける' }))

    expect(screen.queryByText(/書きかけの記録があります/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('ひとこと')).toHaveValue('書きかけ')
    expect(screen.getByLabelText('1セット目の重量（kg）')).toHaveValue('60')
    expect(screen.getByRole('button', { name: '投稿する' })).toBeEnabled()
  })

  test('「続ける」で戻したあとは、何も変えずに × を押しても確かめる（書きかけは入力中の内容）', async () => {
    localStorage.setItem(
      KEY,
      JSON.stringify({
        performedOn: '2026-11-21',
        durationMin: '',
        body: '前の書きかけ',
        visibility: 'public',
        exercises: [],
      }),
    )
    renderApp('/workouts/new')
    await userEvent.click(await screen.findByRole('button', { name: '続ける' }))

    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))

    expect(
      screen.getByRole('dialog', { name: '入力中の記録は保存されません。閉じますか？' }),
    ).toBeInTheDocument()
  })

  test('「破棄する」で消し、空のまま始める', async () => {
    localStorage.setItem(
      KEY,
      JSON.stringify({
        performedOn: '2026-11-21',
        durationMin: '',
        body: '前の書きかけ',
        visibility: 'public',
        exercises: [],
      }),
    )
    renderApp('/workouts/new')

    await userEvent.click(await screen.findByRole('button', { name: '破棄する' }))

    expect(localStorage.getItem(KEY)).toBeNull()
    expect(screen.queryByText(/書きかけの記録があります/)).not.toBeInTheDocument()
    expect(screen.getByLabelText('ひとこと')).toHaveValue('')
  })

  test('案内に答えるまでは、入力しても前の書きかけを上書きしない', async () => {
    const saved = JSON.stringify({
      performedOn: '2026-11-21',
      durationMin: '',
      body: '前の書きかけ',
      visibility: 'public',
      exercises: [],
    })
    localStorage.setItem(KEY, saved)
    renderApp('/workouts/new')
    await screen.findByText(/書きかけの記録があります/)

    await fill('ひとこと', '新しい入力')

    expect(localStorage.getItem(KEY)).toBe(saved)
  })

  test('投稿したら消す', async () => {
    vi.mocked(api.createWorkout).mockResolvedValue(SAVED)
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await writeSomething()

    await userEvent.click(screen.getByRole('button', { name: '投稿する' }))

    await screen.findByText('記録を投稿しました')
    expect(localStorage.getItem(KEY)).toBeNull()
  })

  test('× で入力を捨てて閉じたら消す', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await writeSomething()

    await userEvent.click(screen.getByRole('button', { name: '閉じる' }))
    await userEvent.click(screen.getByRole('button', { name: '閉じる（入力を捨てる）' }))

    await screen.findByRole('heading', { name: 'ホーム' })
    expect(localStorage.getItem(KEY)).toBeNull()
  })

  test('編集では一時保存しない（案内も出さない）', async () => {
    localStorage.setItem(
      KEY,
      JSON.stringify({
        performedOn: '2026-11-21',
        durationMin: '',
        body: '作成の書きかけ',
        visibility: 'public',
        exercises: [],
      }),
    )
    renderApp('/workouts/5/edit')
    await screen.findByRole('heading', { name: '記録を編集' })

    await fill('ひとこと', '編集中')

    expect(screen.queryByText(/書きかけの記録があります/)).not.toBeInTheDocument()
    expect(JSON.parse(localStorage.getItem(KEY) ?? 'null')).toMatchObject({
      body: '作成の書きかけ',
    })
  })

  test('ほかのユーザーの書きかけは出さない', async () => {
    localStorage.setItem(
      'fitlog:workout-draft:sato',
      JSON.stringify({
        performedOn: '2026-11-21',
        durationMin: '',
        body: '佐藤さんの書きかけ',
        visibility: 'public',
        exercises: [],
      }),
    )
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })

    expect(screen.queryByText(/書きかけの記録があります/)).not.toBeInTheDocument()
  })
})

describe('種目を変える・セットの並べ替え', () => {
  test('種目を変えると、記録方式が同じ種目だけから選べ、セットはそのまま', async () => {
    vi.mocked(api.searchExercises).mockResolvedValue([
      BENCH,
      PUSH_UP,
      { ...BENCH, id: 3, name: 'ダンベルプレス' },
    ])
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('ベンチプレス')
    await fill('1セット目の重量（kg）', '30')
    await fill('1セット目の回数', '12')

    await userEvent.click(screen.getByRole('button', { name: '種目を変える' }))
    const picker = await screen.findByRole('dialog', { name: '種目を選ぶ' })
    expect(
      await within(picker).findByRole('button', { name: 'ダンベルプレス' }),
    ).toBeInTheDocument()
    expect(within(picker).queryByRole('button', { name: '腕立て伏せ' })).not.toBeInTheDocument()
    await userEvent.click(within(picker).getByRole('button', { name: 'ダンベルプレス' }))

    expect(screen.getByRole('region', { name: 'ダンベルプレス（胸）' })).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: 'ベンチプレス（胸）' })).not.toBeInTheDocument()
    expect(screen.getByLabelText('1セット目の重量（kg）')).toHaveValue('30')
    expect(screen.getByLabelText('1セット目の回数')).toHaveValue('12')
  })

  test('セットのメニューの「上へ」「下へ」で並べ替える。端は押せない', async () => {
    renderApp('/workouts/new')
    await screen.findByRole('heading', { name: '新しい記録' })
    await addExercise('腕立て伏せ')
    await fill('1セット目の回数', '10')
    await userEvent.click(screen.getByRole('button', { name: '＋ セットを追加' }))
    await fill('2セット目の回数', '20')

    await userEvent.click(screen.getByRole('button', { name: '1セット目のメニュー' }))
    expect(screen.getByRole('menuitem', { name: '上へ' })).toBeDisabled()
    await userEvent.click(screen.getByRole('menuitem', { name: '下へ' }))

    expect(screen.getByLabelText('1セット目の回数')).toHaveValue('20')
    expect(screen.getByLabelText('2セット目の回数')).toHaveValue('10')
    expect(screen.queryByRole('menu')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '2セット目のメニュー' }))
    expect(screen.getByRole('menuitem', { name: '下へ' })).toBeDisabled()
    await userEvent.click(screen.getByRole('menuitem', { name: '上へ' }))
    expect(screen.getByLabelText('1セット目の回数')).toHaveValue('10')
  })
})
