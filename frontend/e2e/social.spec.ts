import { expect, type Page, test } from '@playwright/test'
import {
  expectNoA11yViolations,
  login,
  logout,
  newUser,
  signupByApi,
  workoutByApi,
} from './support.ts'

// 段階 1 の E2E：フォローしてタイムラインに出る、ナイストレーニング・コメント・返信で相手に通知が届く
// （docs/test-plan.md 8章、UC-40・UC-51・UC-52）。2人のユーザーを、ログインし直して切り替える

/**
 * 通知は保存の確定後に別のスレッドで作るので、操作の直後にあるとは限らない。通知一覧を開き直しながら、
 * 決まった時間（5秒）まで待つ（docs/test-plan.md 8章。固定の時間で止めて待たない）
 */
async function expectNotification(page: Page, text: string): Promise<void> {
  await expect
    .poll(
      async () => {
        await page.goto('/notifications')
        await expect(page.getByRole('heading', { level: 1, name: '通知' })).toBeVisible()
        return page.getByText(text).count()
      },
      { timeout: 5_000, message: `「${text}」の通知が届くのを待つ` },
    )
    .toBeGreaterThan(0)
}

test('フォローすると相手の記録がタイムラインに出て、ナイストレーニング・コメント・返信で相手に通知が届く', async ({
  page,
  request,
}) => {
  const me = newUser('fan')
  const author = newUser('author')
  await signupByApi(request, me)
  const authorToken = await signupByApi(request, author)
  await workoutByApi(request, authorToken, '朝トレ完了！')

  // フォローする前は、フォロー中タイムラインに出ない
  await login(page, me)
  await expect(page.getByText('まだ記録がありません。最初の記録をつけてみましょう')).toBeVisible()

  // 相手のプロフィールからフォローすると、フォロー中タイムラインに出る
  await page.goto(`/users/${author.username}`)
  await expect(page.getByRole('heading', { level: 1, name: author.displayName })).toBeVisible()
  await expectNoA11yViolations(page)
  await page.getByRole('button', { name: 'フォローする' }).click()
  await expect(page.getByRole('button', { name: 'フォロー中' })).toBeVisible()
  await page.getByRole('link', { name: 'ホーム' }).click()
  const card = page.getByRole('article', { name: `${author.displayName}の記録` })
  await expect(card.getByText('朝トレ完了！')).toBeVisible()

  // ナイストレーニングする
  const like = card.getByRole('button', { name: /ナイストレーニング/ })
  await like.click()
  await expect(like).toHaveAttribute('aria-pressed', 'true')
  await expect(like).toHaveText(/1$/)

  // 💬 から詳細へ移り、コメントする
  await card.getByRole('link', { name: /コメント欄へ/ }).click()
  const input = page.getByRole('textbox', { name: 'コメントを入力' })
  await expect(input).toBeFocused()
  await input.fill('フォームきれいでした！')
  await page.getByRole('button', { name: 'コメントする' }).click()
  const comments = page.getByRole('region', { name: 'コメント' })
  await expect(comments.getByText('フォームきれいでした！')).toBeVisible()
  await expectNoA11yViolations(page)

  // 相手に、フォロー・ナイストレーニング・コメントの通知が届く
  await logout(page)
  await login(page, author)
  await expectNotification(page, `${me.displayName}さんがあなたをフォローしました`)
  await expectNotification(page, `${me.displayName}さんがあなたの記録にナイストレーニングしました`)
  await expectNotification(page, `${me.displayName}さんがあなたの記録にコメントしました`)
  await expectNoA11yViolations(page)

  // 相手がコメントに返信すると、自分に返信の通知が届く
  await page.getByText(`${me.displayName}さんがあなたの記録にコメントしました`).click()
  await expect(page).toHaveURL(/\/workouts\/\d+/)
  await page.getByRole('region', { name: 'コメント' }).getByRole('button', { name: '返信' }).click()
  await page.getByRole('textbox', { name: '返信を入力' }).fill('ありがとうございます！')
  await page.getByRole('button', { name: '返信する' }).click()
  await expect(page.getByText('ありがとうございます！')).toBeVisible()

  await logout(page)
  await login(page, me)
  await expectNotification(page, `${author.displayName}さんがあなたのコメントに返信しました`)
})
