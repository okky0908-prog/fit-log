import { render, screen } from '@testing-library/react'
import { describe, expect, test } from 'vitest'
import { NotificationLink } from './NotificationLink'

// ナビゲーションの「🔔 と未読の数」。押すと通知一覧（S-30）へ（docs/screens.md 共通部品・F-112）。
// 境界値：0（数字を出さない）・1・99・100（「99+」）（docs/test-plan.md 5.1）
describe('NotificationLink', () => {
  test.each([
    // 未読の数, 出す数字, 読み上げる名前
    [1, '1', '通知（未読 1件）'],
    [99, '99', '通知（未読 99件）'],
    [100, '99+', '通知（未読 99件以上）'],
  ])('未読が%i件なら、%s と出す', (count, text, name) => {
    render(<NotificationLink count={count} />)

    expect(screen.getByRole('link', { name })).toHaveTextContent(text)
  })

  test('未読が0件なら、数字を出さない', () => {
    render(<NotificationLink count={0} />)

    expect(screen.getByRole('link', { name: '通知' })).toHaveTextContent(/^🔔$/u)
  })

  test('通知一覧へのリンクになっている', () => {
    render(<NotificationLink count={3} />)

    expect(screen.getByRole('link')).toHaveAttribute('href', '/notifications')
  })
})
