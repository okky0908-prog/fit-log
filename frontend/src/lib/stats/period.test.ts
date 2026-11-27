import { describe, expect, test } from 'vitest'
import {
  addDays,
  addMonths,
  hasNextMonth,
  hasNextWeek,
  monthFromParam,
  monthLabel,
  weekFromParam,
  weekLabel,
  weekStartOf,
} from './period'

// 期間の切り替え（docs/screens.md）。週の区切り・月をまたぐ・年をまたぐ・うるう年（docs/test-plan.md 4.4）

describe('週の始まり（月曜）', () => {
  test('月曜はその日、日曜は前の月曜', () => {
    expect(weekStartOf('2026-11-09')).toBe('2026-11-09')
    expect(weekStartOf('2026-11-12')).toBe('2026-11-09')
    expect(weekStartOf('2026-11-15')).toBe('2026-11-09')
    expect(weekStartOf('2026-11-16')).toBe('2026-11-16')
  })

  test('月と年をまたぐ', () => {
    expect(weekStartOf('2026-11-01')).toBe('2026-10-26')
    expect(weekStartOf('2027-01-01')).toBe('2026-12-28')
  })
})

describe('日付と月の足し算', () => {
  test('日を足す・引く（月末・年末・うるう年をまたぐ）', () => {
    expect(addDays('2026-11-30', 1)).toBe('2026-12-01')
    expect(addDays('2026-12-28', 7)).toBe('2027-01-04')
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29')
    expect(addDays('2026-11-09', -7)).toBe('2026-11-02')
  })

  test('月を足す・引く（年をまたぐ）', () => {
    expect(addMonths('2026-11', 1)).toBe('2026-12')
    expect(addMonths('2026-12', 1)).toBe('2027-01')
    expect(addMonths('2026-01', -1)).toBe('2025-12')
  })
})

describe('URL の期間', () => {
  const today = '2026-11-12'

  test('週：日付があれば、その週の月曜', () => {
    expect(weekFromParam('2026-11-04', today)).toBe('2026-11-02')
  })

  test('週：なければ・正しくなければ・未来の週なら、今週', () => {
    expect(weekFromParam(null, today)).toBe('2026-11-09')
    expect(weekFromParam('abc', today)).toBe('2026-11-09')
    expect(weekFromParam('2026-13-01', today)).toBe('2026-11-09')
    expect(weekFromParam('2026-02-30', today)).toBe('2026-11-09')
    expect(weekFromParam('2026-11-16', today)).toBe('2026-11-09')
  })

  test('週：今週の日曜（まだ来ていない日）でも、今週', () => {
    expect(weekFromParam('2026-11-15', today)).toBe('2026-11-09')
  })

  test('月：あればその月、なければ・正しくなければ・未来の月なら、今月', () => {
    expect(monthFromParam('2026-10', today)).toBe('2026-10')
    expect(monthFromParam(null, today)).toBe('2026-11')
    expect(monthFromParam('2026-13', today)).toBe('2026-11')
    expect(monthFromParam('2026-1', today)).toBe('2026-11')
    expect(monthFromParam('2026-12', today)).toBe('2026-11')
  })
})

describe('次の期間へ移れるか（未来には移動できない）', () => {
  test('週：次の週の月曜が今日以前なら移れる', () => {
    expect(hasNextWeek('2026-11-02', '2026-11-09')).toBe(true)
    expect(hasNextWeek('2026-11-02', '2026-11-08')).toBe(false)
    expect(hasNextWeek('2026-11-09', '2026-11-12')).toBe(false)
  })

  test('月：次の月が今月以前なら移れる', () => {
    expect(hasNextMonth('2026-10', '2026-11-01')).toBe(true)
    expect(hasNextMonth('2026-10', '2026-10-31')).toBe(false)
    expect(hasNextMonth('2025-12', '2026-01-01')).toBe(true)
  })
})

describe('期間の表示', () => {
  test('週：「2026/11/09（月）〜 11/15（日）」', () => {
    expect(weekLabel('2026-11-09')).toBe('2026/11/09（月）〜 11/15（日）')
  })

  test('週：年をまたぐなら、終わりの日にも年を付ける', () => {
    expect(weekLabel('2026-12-28')).toBe('2026/12/28（月）〜 2027/01/03（日）')
  })

  test('月：「2026年11月」', () => {
    expect(monthLabel('2026-11')).toBe('2026年11月')
    expect(monthLabel('2027-01')).toBe('2027年1月')
  })
})
