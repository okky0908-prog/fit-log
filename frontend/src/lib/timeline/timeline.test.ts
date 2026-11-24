import { expect, test } from 'vitest'
import { newPostsLabel } from './timeline'

// 新しい投稿のお知らせ（docs/screens.md S-10：99件を超えたら「99+件」。API は 100件で数えるのをやめる）

test('0件なら出さず、1〜99件はそのまま、100件は「99+件」', () => {
  expect(newPostsLabel(0)).toBeNull()
  expect(newPostsLabel(1)).toBe('↑ 1件の新しい投稿')
  expect(newPostsLabel(99)).toBe('↑ 99件の新しい投稿')
  expect(newPostsLabel(100)).toBe('↑ 99+件の新しい投稿')
})
