import { screen } from '@testing-library/react'
import { expect, test, vi } from 'vitest'
import { ME, renderApp, startAs } from '../test/helpers'

vi.mock('../lib/api/client', (original) =>
  import('../test/apiMock').then((m) => m.mockApiClient(original)),
)

// S-10 ホーム。タイムラインは 12 で作る。記録の作成への入口は、ナビゲーション（10）ができるまでここに置く

test('「記録する」から、記録の作成の画面へ行ける', async () => {
  startAs(ME)

  renderApp('/')

  expect(await screen.findByRole('link', { name: '記録する' })).toHaveAttribute(
    'href',
    '/workouts/new',
  )
})
