import { describe, expect, test } from 'vitest'
import { calendarCells, dayFromParam, dayLabel } from './calendar'

// 記録の画面のカレンダー（月曜始まり。docs/screens.md S-20）

describe('カレンダーのます', () => {
  test('1日の曜日まで空けて、1日から末日まで並べる', () => {
    // 2026-11-01 は日曜なので、月〜土の6つを空ける
    const cells = calendarCells('2026-11')

    expect(cells.slice(0, 7)).toEqual([null, null, null, null, null, null, '2026-11-01'])
    expect(cells.at(-1)).toBe('2026-11-30')
    expect(cells.filter((cell) => cell !== null)).toHaveLength(30)
  })

  test('月曜から始まる月は空けない。うるう年の二月は29日まで', () => {
    // 2027-02-01 は月曜
    expect(calendarCells('2027-02')[0]).toBe('2027-02-01')
    expect(calendarCells('2028-02').at(-1)).toBe('2028-02-29')
  })
})

describe('URL の日付', () => {
  test('表示中の月の、今日までの正しい日付なら、その日', () => {
    expect(dayFromParam('2026-11-04', '2026-11', '2026-11-12')).toBe('2026-11-04')
    expect(dayFromParam('2026-11-12', '2026-11', '2026-11-12')).toBe('2026-11-12')
  })

  test('なければ・ほかの月・未来・正しくない日付なら、選ばない', () => {
    expect(dayFromParam(null, '2026-11', '2026-11-12')).toBeNull()
    expect(dayFromParam('2026-10-31', '2026-11', '2026-11-12')).toBeNull()
    expect(dayFromParam('2026-11-13', '2026-11', '2026-11-12')).toBeNull()
    expect(dayFromParam('2026-11-31', '2026-11', '2026-11-12')).toBeNull()
    expect(dayFromParam('abc', '2026-11', '2026-11-12')).toBeNull()
  })
})

test('日付の読み上げの名前は「11月4日（水）」', () => {
  expect(dayLabel('2026-11-04')).toBe('11月4日（水）')
})
