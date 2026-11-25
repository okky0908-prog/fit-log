// E2E テストで共通して使うもの（アクセシビリティの確認など）。ユーザーを作る・API で下準備するものは、使うテストを書くときに足す
import AxeBuilder from '@axe-core/playwright'
import { expect, type Page } from '@playwright/test'

/**
 * 画面にアクセシビリティの違反（axe。WCAG 2.1 AA）がないことを確かめる（docs/test-plan.md 8章：画面を作る段階から axe を入れる）。
 * 違反があれば、どの規則のどの要素かを失敗の内容に出す
 */
export async function expectNoA11yViolations(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const violations = results.violations.map((v) => ({
    id: v.id,
    help: v.help,
    targets: v.nodes.map((node) => node.target.join(' ')),
  }))
  expect(violations).toEqual([])
}
