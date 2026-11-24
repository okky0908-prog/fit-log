import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import * as api from '../lib/api/client'
import { ICON_MAX_SIDE, shrinkImage, UnusableImageError } from '../lib/image/shrink'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)
// 画像の縮小は Canvas を使うので jsdom では動かない。縮小の決まりは lib/image/shrink.test.ts で確かめる
vi.mock('../lib/image/shrink', async (original) => ({
  ...(await original<typeof import('../lib/image/shrink')>()),
  shrinkImage: vi.fn<typeof import('../lib/image/shrink').shrinkImage>(),
}))

// S-41 プロフィール編集（docs/screens.md）

const ME_WITH_BIO = { ...ME, bio: '週4で筋トレしています' }

function photo(name: string): File {
  return new File(['x'], name, { type: 'image/png' })
}

beforeEach(() => {
  vi.mocked(api.updateProfile).mockReset()
  vi.mocked(api.updateProfile).mockImplementation(async (input) => ({
    username: ME.username,
    displayName: input.displayName,
    iconUrl: null,
    isPrivate: false,
    bio: input.bio,
    followingCount: 0,
    followerCount: 0,
    me: true,
    followStatus: 'none',
    canViewRecords: true,
  }))
  vi.mocked(api.fetchMe).mockReset()
  vi.mocked(api.fetchProfile).mockReset()
  // 保存したあとに移るプロフィールの画面は、読み込み中のままにする
  vi.mocked(api.fetchProfile).mockReturnValue(new Promise(() => {}))
  vi.mocked(shrinkImage).mockReset()
  vi.mocked(shrinkImage).mockImplementation(
    async (file) => new Blob([`shrunk:${file.name}`], { type: 'image/jpeg' }),
  )
  startAs(ME_WITH_BIO)
})

test('今の表示名・自己紹介・ユーザー名を出し、自己紹介の文字数を数える', async () => {
  renderApp('/settings/profile')

  expect(
    await screen.findByRole('heading', { level: 1, name: 'プロフィールを編集' }),
  ).toBeInTheDocument()
  expect(screen.getByLabelText('表示名')).toHaveValue('山田太郎')
  expect(screen.getByLabelText('自己紹介')).toHaveValue('週4で筋トレしています')
  expect(screen.getByText('11 / 160')).toBeInTheDocument()
  expect(screen.getByText(/@yamada/)).toBeInTheDocument()

  await userEvent.type(screen.getByLabelText('自己紹介'), '💪')
  expect(screen.getByText('12 / 160')).toBeInTheDocument()
})

describe('保存', () => {
  test('保存すると、ログイン中の情報を取り直し、自分のプロフィールへ戻ってお知らせを出す', async () => {
    vi.mocked(api.fetchMe).mockResolvedValue({ ...ME_WITH_BIO, displayName: '山田花子' })
    const { router } = renderApp('/settings/profile')

    const name = await screen.findByLabelText('表示名')
    await userEvent.clear(name)
    await userEvent.type(name, '山田花子')
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(api.updateProfile).toHaveBeenCalledWith(
      { displayName: '山田花子', bio: '週4で筋トレしています' },
      undefined,
    )
    await waitFor(() => expect(router.state.location.pathname).toBe('/users/yamada'))
    expect(await screen.findByText('プロフィールを更新しました')).toBeInTheDocument()
    expect(api.fetchMe).toHaveBeenCalled()
  })

  test('入力に誤りがあれば、送らずに項目の下に出す', async () => {
    renderApp('/settings/profile')

    await userEvent.clear(await screen.findByLabelText('表示名'))
    await userEvent.type(screen.getByLabelText('自己紹介'), 'あ'.repeat(151))
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))

    expect(screen.getByText('表示名は1〜50文字で入力してください')).toBeInTheDocument()
    expect(screen.getByText('自己紹介は160文字までで入力してください')).toBeInTheDocument()
    expect(screen.getByLabelText('表示名')).toHaveAttribute('aria-invalid', 'true')
    expect(api.updateProfile).not.toHaveBeenCalled()
  })

  test('サーバーの項目の誤りは項目の下に、それ以外は上に出す', async () => {
    vi.mocked(api.updateProfile)
      .mockRejectedValueOnce(
        new api.ApiError(400, 'VALIDATION_FAILED', '入力内容に誤りがあります', [
          { field: 'icon', message: '位置情報を含む画像はアップロードできません' },
        ]),
      )
      .mockRejectedValueOnce(
        new api.ApiError(500, 'IMAGE_UPLOAD_FAILED', '画像を保存できませんでした'),
      )
    renderApp('/settings/profile')

    await userEvent.upload(await screen.findByLabelText('アイコン画像を選ぶ'), photo('a.png'))
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))
    expect(
      await screen.findByText('位置情報を含む画像はアップロードできません'),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: '保存する' }))
    expect(await screen.findByText('画像を保存できませんでした')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '保存する' })).toBeEnabled()
  })
})

describe('アイコン', () => {
  test('選んだ画像を512pxに縮小して見本を出し、保存で一緒に送る', async () => {
    renderApp('/settings/profile')

    await userEvent.upload(await screen.findByLabelText('アイコン画像を選ぶ'), photo('a.png'))

    expect(shrinkImage).toHaveBeenCalledWith(expect.any(File), undefined, ICON_MAX_SIDE)
    const main = screen.getByRole('main')
    await waitFor(() =>
      expect(within(main).getByTestId('avatar')).toHaveAttribute(
        'src',
        expect.stringMatching(/^blob:/),
      ),
    )
    await userEvent.click(screen.getByRole('button', { name: '保存する' }))
    const sent = vi.mocked(api.updateProfile).mock.calls[0][1]
    expect(await sent?.text()).toBe('shrunk:a.png')
  })

  test('使えない画像なら、理由を出して見本は変えない', async () => {
    vi.mocked(shrinkImage).mockRejectedValue(new UnusableImageError())
    renderApp('/settings/profile')

    await userEvent.upload(await screen.findByLabelText('アイコン画像を選ぶ'), photo('a.png'))

    expect(
      await screen.findByText('この画像は使えません（JPEG・PNG・WebP、20MBまで）'),
    ).toBeInTheDocument()
    expect(within(screen.getByRole('main')).getByTestId('avatar')).toHaveTextContent('山')
  })
})

describe('キャンセル', () => {
  test('変更がなければ、そのまま自分のプロフィールへ戻る', async () => {
    const { router } = renderApp('/settings/profile')

    await userEvent.click(await screen.findByRole('button', { name: 'キャンセル' }))

    expect(router.state.location.pathname).toBe('/users/yamada')
  })

  test('変更があれば確認し、「戻る」で戻り、キャンセルなら残る', async () => {
    const { router } = renderApp('/settings/profile')
    await userEvent.type(await screen.findByLabelText('自己紹介'), '！')

    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
    const dialog = screen.getByRole('dialog', { name: '変更は保存されません。よろしいですか？' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'キャンセル' }))
    expect(router.state.location.pathname).toBe('/settings/profile')
    expect(screen.getByLabelText('自己紹介')).toHaveValue('週4で筋トレしています！')

    await userEvent.click(screen.getByRole('button', { name: 'キャンセル' }))
    await userEvent.click(screen.getByRole('button', { name: '戻る' }))
    expect(router.state.location.pathname).toBe('/users/yamada')
  })
})
