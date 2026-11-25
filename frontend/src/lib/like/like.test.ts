import { describe, expect, test } from 'vitest'
import { toggleLike } from './like'

// ナイストレーニングのボタンの、押した瞬間の値（docs/screens.md「投稿カード」）

describe('押したあとの状態', () => {
  test('付けていなければ付けて1増やし、付けていれば取り消して1減らす', () => {
    expect(toggleLike({ likeCount: 0, likedByMe: false })).toEqual({
      likeCount: 1,
      likedByMe: true,
    })
    expect(toggleLike({ likeCount: 12, likedByMe: true })).toEqual({
      likeCount: 11,
      likedByMe: false,
    })
  })
})
