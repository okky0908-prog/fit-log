import { expect, test } from '@playwright/test'
import { expectNoA11yViolations, login, newUser, signupByApi, workoutByApi } from './support.ts'

// 種目ごとの推移（S-22）。段階 2 の 4-2。記録の画面から種目の一覧・推移へ移り、自己ベスト・推定 1RM・折れ線グラフ・記録の一覧が出ること、
// 記録の一覧からその記録の詳細へ移れること、アクセシビリティの違反がないことを、本物のブラウザで確かめる

test('記録した種目を選んで、自己ベストと推移を見られる', async ({ page, request }) => {
  const user = newUser('trend')
  const token = await signupByApi(request, user)
  // ベンチプレス（ID 1）60kg × 10回 × 3セット。推定 1RM は 60 ×（1 + 10 ÷ 30）＝ 80kg
  await workoutByApi(request, token, '', [
    { exerciseId: 1, sets: [1, 2, 3].map(() => ({ weight: 60, reps: 10 })) },
  ])
  await login(page, user)

  await page
    .getByRole('navigation', { name: 'メイン' })
    .getByRole('link', { name: '記録', exact: true })
    .click()
  await page.getByRole('link', { name: '種目 ›' }).click()
  await expect(page.getByRole('heading', { level: 1, name: '種目ごとの推移' })).toBeVisible()
  const chest = page.getByRole('region', { name: '胸' })
  await expect(chest.getByRole('link', { name: /ベンチプレス/ })).toBeVisible()
  await expectNoA11yViolations(page)

  await chest.getByRole('link', { name: /ベンチプレス/ }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'ベンチプレス' })).toBeVisible()
  const best = page.getByRole('region', { name: 'ベスト' })
  await expect(best.getByText(/^60kg（\d{4}\/\d{2}\/\d{2}）$/)).toBeVisible()
  await expect(best.getByText(/^80kg（\d{4}\/\d{2}\/\d{2}）$/)).toBeVisible()

  // グラフは後から読み込まれて SVG で描かれる
  const chart = page.getByRole('region', { name: '推移' })
  await expect(chart.locator('svg.recharts-surface')).toBeVisible()
  await chart.getByRole('button', { name: '推定 1RM' }).click()
  await chart.getByRole('button', { name: '表で見る' }).click()
  await expect(chart.getByRole('table').getByRole('cell', { name: '80kg' })).toBeVisible()
  await expectNoA11yViolations(page)

  await page
    .getByRole('region', { name: '記録の一覧' })
    .getByRole('link', { name: /最大 60kg × 10回・推定 1RM 80kg/ })
    .click()
  await expect(page).toHaveURL(/\/workouts\/\d+$/)
})
