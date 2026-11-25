import { describe, expect, test } from 'vitest'
import { withWorkoutValues } from './workoutCache'

// 読み込み済みのデータの中の、記録の値の書き換え（ナイストレーニング・コメントの操作のあと）

describe('読み込み済みのデータの書き換え', () => {
  const liked = { likeCount: 5, likedByMe: true }

  test('コメント数だけを書き換えることもできる', () => {
    const workout = { id: 1, likeCount: 4, likedByMe: false, commentCount: 2 }

    expect(withWorkoutValues(workout, 1, { commentCount: 3 })).toEqual({
      ...workout,
      commentCount: 3,
    })
  })

  test('記録の詳細は、同じ記録なら書き換え、違う記録ならそのまま', () => {
    const workout = { id: 1, body: '脚の日', likeCount: 4, likedByMe: false }

    expect(withWorkoutValues(workout, 1, liked)).toEqual({ ...workout, ...liked })
    expect(withWorkoutValues(workout, 2, liked)).toBe(workout)
  })

  test('一覧のページは、その記録の行だけを書き換える', () => {
    const data = {
      pages: [
        {
          items: [
            { id: 1, likeCount: 4, likedByMe: false },
            { id: 2, likeCount: 0, likedByMe: false },
          ],
        },
        { items: [{ id: 3, likeCount: 1, likedByMe: false }] },
      ],
      pageParams: [null, 'next'],
    }

    const result = withWorkoutValues(data, 1, liked)

    expect(result.pages[0].items[0]).toEqual({ id: 1, ...liked })
    expect(result.pages[0].items[1]).toBe(data.pages[0].items[1])
    expect(result.pages[1]).toBe(data.pages[1])
    expect(result.pageParams).toBe(data.pageParams)
  })

  test('記録でないデータ（プロフィール・ユーザーの一覧）と、まだないデータはそのまま', () => {
    const profile = { username: 'sato', followerCount: 8 }
    const users = { pages: [{ items: [{ username: 'sato', followStatus: 'none' }] }] }

    expect(withWorkoutValues(profile, 1, liked)).toBe(profile)
    expect(withWorkoutValues(users, 1, liked)).toBe(users)
    expect(withWorkoutValues(undefined, 1, liked)).toBeUndefined()
  })

  test('ID はあるが記録でないもの（公開した目標など）は、同じ ID でも書き換えない', () => {
    const goal = { id: 1, name: '月間ランニング 100km' }
    const goals = { pages: [{ items: [goal] }] }

    expect(withWorkoutValues(goal, 1, liked)).toBe(goal)
    expect(withWorkoutValues(goals, 1, liked)).toBe(goals)
  })
})
