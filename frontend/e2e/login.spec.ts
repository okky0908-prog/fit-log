import { expect, test } from '@playwright/test'
import { expectNoA11yViolations } from './support.ts'

// E2E の1本目（docs/test-plan.md 8.1：E2E の起動と Playwright は「ログイン画面が開く」1本から）

test('ログインしていなければ、ログイン画面が開き、アクセシビリティの違反がない', async ({
  page,
}) => {
  await page.goto('/')

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { level: 1, name: 'ログイン' })).toBeVisible()
  await expect(page.getByLabel('メールアドレス')).toBeVisible()
  await expect(page.getByLabel('パスワード')).toBeVisible()
  await expectNoA11yViolations(page)
})
