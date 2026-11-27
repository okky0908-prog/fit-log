import { expect, test } from '@playwright/test'
import { expectNoA11yViolations, login, newUser, signupByApi, workoutByApi } from './support.ts'

// ランニングの記録（S-21）。段階 2 の 3-2。記録の画面から開き、集計・ベスト記録・年間のグラフが出ること、
// ベスト記録からその記録の詳細へ移れること、アクセシビリティの違反がないことを、本物のブラウザで確かめる

test('ランニングの集計とベスト記録が出て、年間は月ごとのグラフで見られる', async ({
  page,
  request,
}) => {
  const user = newUser('running')
  const token = await signupByApi(request, user)
  // ランニング（ID 48）：30分 5km と、2分 0.5km（ペースは速いが 1km 未満なので、最速ペースの対象外）
  await workoutByApi(request, token, '', [
    {
      exerciseId: 48,
      sets: [
        { durationSec: 1800, distance: 5 },
        { durationSec: 120, distance: 0.5 },
      ],
    },
  ])
  await login(page, user)

  await page
    .getByRole('navigation', { name: 'メイン' })
    .getByRole('link', { name: '記録', exact: true })
    .click()
  await page.getByRole('link', { name: 'ランニング ›' }).click()
  await expect(page.getByRole('heading', { level: 1, name: 'ランニング' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '月間' })).toHaveAttribute('aria-selected', 'true')

  const stats = page.getByRole('region', { name: '集計' })
  await expect(stats.getByText('5.50km', { exact: true })).toBeVisible()
  await expect(stats.getByText('32:00', { exact: true })).toBeVisible()
  await expect(stats.getByText('2回', { exact: true })).toBeVisible()
  // (1800 + 120) 秒 ÷ 5.5km ＝ 349.09… → 5:49/km
  await expect(stats.getByText('5:49/km', { exact: true })).toBeVisible()

  // 横に並べた要素の間には、読み上げの名前に空白が入る（ブラウザによる）
  const best = page.getByRole('region', { name: 'ベスト記録' })
  await expect(
    best.getByRole('link', { name: /最速ペース 6:00\/km ?（5\.00km・30:00・/ }),
  ).toBeVisible()
  await expect(best.getByRole('link', { name: /最長距離 5\.00km ?（30:00・/ })).toBeVisible()
  await expectNoA11yViolations(page)

  await page.getByRole('tab', { name: '年間' }).click()
  await expect(page).toHaveURL(/\/records\/running\?period=year$/)
  const chart = page.getByRole('region', { name: '月ごとの走行距離' })
  await expect(chart.locator('svg.recharts-surface')).toBeVisible()
  await chart.getByRole('button', { name: '表で見る' }).click()
  await expect(chart.getByRole('table').getByRole('cell', { name: '5.50km' })).toBeVisible()
  await expectNoA11yViolations(page)

  await best.getByRole('link', { name: /最長距離/ }).click()
  await expect(page).toHaveURL(/\/workouts\/\d+$/)
  await expect(page.getByText('ランニング')).toBeVisible()
})
