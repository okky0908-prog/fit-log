import { expect, test } from '@playwright/test'
import { fileURLToPath } from 'node:url'
import { expectNoA11yViolations, login, newUser, signupByApi } from './support.ts'

// 段階 1 の E2E：記録を作る（画像つき）（docs/test-plan.md 8章、UC-10）

const IMAGE = fileURLToPath(new URL('./fixtures/workout.png', import.meta.url))

test('種目・セット・画像を入れて投稿すると、詳細に出て、画像も表示される', async ({
  page,
  request,
}) => {
  const user = newUser('workout')
  await signupByApi(request, user)
  await login(page, user)

  await page
    .getByRole('link', { name: /記録する/ })
    .first()
    .click()
  await expect(page.getByRole('heading', { level: 1, name: '新しい記録' })).toBeVisible()
  await page.getByRole('button', { name: '＋ 種目を追加' }).click()
  const picker = page.getByRole('dialog', { name: '種目を選ぶ' })
  await picker.getByRole('button', { name: 'ベンチプレス', exact: true }).click()
  await page.getByLabel('1セット目の重量（kg）').fill('60')
  await page.getByLabel('1セット目の回数').fill('10')
  await page.getByRole('button', { name: '＋ セットを追加' }).click()
  await page.getByLabel('2セット目の回数').fill('8')
  await page.getByLabel('ひとこと').fill('胸の日 #胸トレ')
  await page.getByLabel('画像を追加').setInputFiles(IMAGE)
  await expect(page.getByRole('button', { name: '画像 1 を外す' })).toBeVisible()
  await expectNoA11yViolations(page)
  await page.getByRole('button', { name: '投稿する' }).click()

  // 詳細の画面へ移る
  await expect(page.getByText('記録を投稿しました')).toBeVisible()
  await expect(page).toHaveURL(/\/workouts\/\d+$/)
  const exercise = page.getByRole('region', { name: 'ベンチプレス（胸）' })
  await expect(exercise.getByText('60kg × 10回')).toBeVisible()
  await expect(exercise.getByText('60kg × 8回')).toBeVisible()
  await expect(page.getByText('胸の日 #胸トレ')).toBeVisible()
  // 画像は S3（LocalStack）に保存され、署名付き URL で読み込める（読み込めなければ大きさが 0 になる）
  const image = page.getByRole('img', { name: '画像 1 / 1' })
  await expect(image).toBeVisible()
  await expect
    .poll(() => image.evaluate((img: HTMLImageElement) => img.naturalWidth))
    .toBeGreaterThan(0)
  await expectNoA11yViolations(page)

  // ホームのフォロー中タイムラインにも、自分の記録が出る
  await page.getByRole('link', { name: 'ホーム' }).click()
  const card = page.getByRole('article', { name: `${user.displayName}の記録` })
  await expect(card.getByText('ベンチプレス')).toBeVisible()
  await expect(card.getByText('2セット（最大 60kg × 10回）')).toBeVisible()
  await expectNoA11yViolations(page)
})
