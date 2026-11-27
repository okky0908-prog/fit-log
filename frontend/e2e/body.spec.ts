import { expect, test } from '@playwright/test'
import { expectNoA11yViolations, login, newUser, signupByApi } from './support.ts'

// 体重の記録（S-25）。段階 2 の 5-2。アイコンのメニューから開き、記録・上書き・推移のグラフ・削除ができること、
// アクセシビリティの違反がないことを、本物のブラウザで確かめる

test('体重を記録して上書きし、推移を見て、削除できる', async ({ page, request }) => {
  const user = newUser('body')
  await signupByApi(request, user)
  await login(page, user)

  await page.getByRole('button', { name: '自分のメニュー' }).click()
  await page.getByRole('menuitem', { name: '体重の記録' }).click()
  await expect(page.getByRole('heading', { level: 1, name: '体重の記録' })).toBeVisible()
  await expect(page.getByText('まだ体重の記録がありません')).toBeVisible()
  await expectNoA11yViolations(page)

  await page.getByLabel('体重（kg）').fill('73.0')
  await page.getByLabel('体脂肪率（%・任意）').fill('18.5')
  await page.getByRole('button', { name: '記録する' }).click()
  await expect(page.getByText('体重を記録しました')).toBeVisible()
  const list = page.getByRole('region', { name: '記録の一覧' })
  await expect(list.getByRole('listitem')).toHaveCount(1)
  await expect(list.getByText('73.0kg')).toBeVisible()

  // 今日の記録があるので「上書きする」になる
  await page.getByLabel('体重（kg）').fill('72.8')
  await page.getByRole('button', { name: '上書きする' }).click()
  await expect(page.getByText('体重を上書きしました')).toBeVisible()
  await expect(list.getByText('72.8kg')).toBeVisible()

  // グラフは後から読み込まれて SVG で描かれる（凡例のアイコンも SVG なので、最初の1つがグラフの本体）
  const chart = page.getByRole('region', { name: '推移' })
  await expect(chart.locator('svg.recharts-surface').first()).toBeVisible()
  await expect(chart.locator('.recharts-line')).toHaveCount(2)
  await chart.getByRole('button', { name: '表で見る' }).click()
  await expect(chart.getByRole('table').getByRole('cell', { name: '72.8kg' })).toBeVisible()
  await expectNoA11yViolations(page)

  await list.getByRole('button', { name: /の記録を削除$/ }).click()
  await page.getByRole('dialog').getByRole('button', { name: '削除する' }).click()
  await expect(page.getByText('体重の記録を削除しました')).toBeVisible()
  await expect(page.getByText('まだ体重の記録がありません')).toBeVisible()
})
