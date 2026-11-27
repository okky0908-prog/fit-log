import { expect, test } from '@playwright/test'
import { expectNoA11yViolations, login, newUser, signupByApi, workoutByApi } from './support.ts'

// 記録（ダッシュボード）の週間・月間（S-20）。段階 2 の 2-3。グラフ（Recharts）が後から読み込まれて描かれること、
// 「表で見る」で同じ値を読めること、アクセシビリティの違反がないことを、本物のブラウザで確かめる

test('今週の記録が集計とグラフに出て、表でも読め、月間に切り替えられる', async ({
  page,
  request,
}) => {
  const user = newUser('records')
  const token = await signupByApi(request, user)
  // ベンチプレス（ID 1）60kg × 10回 × 3セット、ランニング（ID 48）30分 5km
  await workoutByApi(request, token, '', [
    { exerciseId: 1, sets: [1, 2, 3].map(() => ({ weight: 60, reps: 10 })) },
    { exerciseId: 48, sets: [{ durationSec: 1800, distance: 5 }] },
  ])
  await login(page, user)

  await page
    .getByRole('navigation', { name: 'メイン' })
    .getByRole('link', { name: '記録', exact: true })
    .click()
  await expect(page.getByRole('heading', { level: 1, name: '記録' })).toBeVisible()
  const stats = page.getByRole('region', { name: '集計' })
  await expect(stats.getByText('1日', { exact: true })).toBeVisible()
  await expect(stats.getByText('1,800kg')).toBeVisible()
  await expect(stats.getByText('30分')).toBeVisible()
  await expect(stats.getByText('5.00km')).toBeVisible()

  // グラフは後から読み込まれて SVG で描かれる
  const chart = page.getByRole('region', { name: '曜日ごと' })
  await expect(chart.locator('svg.recharts-surface')).toBeVisible()
  await chart.getByRole('button', { name: '表で見る' }).click()
  await expect(chart.getByRole('table').getByRole('cell', { name: '1,800kg' })).toBeVisible()
  await chart.getByRole('button', { name: '運動時間' }).click()
  await expect(chart.getByRole('table').getByRole('cell', { name: '30分' })).toBeVisible()

  await expect(
    page.getByRole('region', { name: '部位別のセット数' }).getByText('3セット（75%）'),
  ).toBeVisible()
  await expectNoA11yViolations(page)

  await page.getByRole('tab', { name: '月間' }).click()
  await expect(page).toHaveURL(/\/records\/month$/)
  await expect(
    page.getByRole('region', { name: '週ごと' }).locator('svg.recharts-surface'),
  ).toBeVisible()
  await expect(page.getByRole('region', { name: '集計' }).getByText('1,800kg')).toBeVisible()
  await expectNoA11yViolations(page)
})
