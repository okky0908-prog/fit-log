import { describe, expect, test } from 'vitest'
import type { BestRun, RunningSummary } from '../api/types'
import {
  bestPaceText,
  hasNextRunning,
  longestRunText,
  monthlyDistancePoints,
  runningLabel,
  runningPeriodFromParams,
  runningStart,
  runningSummaryItems,
  shiftRunning,
} from './running'

// S-21 ランニングの記録の期間と表示（docs/screens.md S-21、F-83）

/** 日本時間の今日（木曜） */
const TODAY = '2026-11-12'

function summary(values: Partial<RunningSummary> = {}): RunningSummary {
  return { distanceKm: 0, durationSec: 0, runCount: 0, avgPaceSecPerKm: null, ...values }
}

function run(values: Partial<BestRun> = {}): BestRun {
  return {
    workoutId: 1,
    performedOn: '2026-11-08',
    distanceKm: 10,
    durationSec: 3120,
    paceSecPerKm: 312,
    ...values,
  }
}

describe('期間の始まりの日', () => {
  test('週は月曜、月は1日、年は1月1日', () => {
    expect(runningStart('week', '2026-11-15')).toBe('2026-11-09')
    expect(runningStart('month', '2026-11-15')).toBe('2026-11-01')
    expect(runningStart('year', '2026-11-15')).toBe('2026-01-01')
  })
})

describe('URL から表示する期間', () => {
  test('period がなければ・知らない値なら月間、date がなければ今日を含む期間', () => {
    expect(runningPeriodFromParams(null, null, TODAY)).toEqual({
      unit: 'month',
      start: '2026-11-01',
    })
    expect(runningPeriodFromParams('day', null, TODAY)).toEqual({
      unit: 'month',
      start: '2026-11-01',
    })
    expect(runningPeriodFromParams('week', null, TODAY)).toEqual({
      unit: 'week',
      start: '2026-11-09',
    })
    expect(runningPeriodFromParams('year', null, TODAY)).toEqual({
      unit: 'year',
      start: '2026-01-01',
    })
  })

  test('date は期間の中のどの日でもよく、期間の始まりの日にそろえる', () => {
    expect(runningPeriodFromParams('week', '2026-10-01', TODAY)).toEqual({
      unit: 'week',
      start: '2026-09-28',
    })
    expect(runningPeriodFromParams('year', '2025-06-30', TODAY)).toEqual({
      unit: 'year',
      start: '2025-01-01',
    })
  })

  test('正しい日付でなければ・未来の期間なら、今日を含む期間', () => {
    expect(runningPeriodFromParams('month', '2026-02-30', TODAY).start).toBe('2026-11-01')
    expect(runningPeriodFromParams('month', 'abc', TODAY).start).toBe('2026-11-01')
    expect(runningPeriodFromParams('month', '2026-12-01', TODAY).start).toBe('2026-11-01')
    expect(runningPeriodFromParams('year', '2027-01-01', TODAY).start).toBe('2026-01-01')
  })

  test('今日より後でも、今日と同じ期間の日なら、その期間（境界：今週の日曜）', () => {
    expect(runningPeriodFromParams('week', '2026-11-15', TODAY).start).toBe('2026-11-09')
    expect(runningPeriodFromParams('week', '2026-11-16', TODAY).start).toBe('2026-11-09')
  })
})

describe('前後の期間', () => {
  test('週は7日、月は1か月、年は1年ずらす（年・月をまたぐ）', () => {
    expect(shiftRunning({ unit: 'week', start: '2026-12-28' }, 1)).toEqual({
      unit: 'week',
      start: '2027-01-04',
    })
    expect(shiftRunning({ unit: 'month', start: '2026-01-01' }, -1)).toEqual({
      unit: 'month',
      start: '2025-12-01',
    })
    expect(shiftRunning({ unit: 'year', start: '2026-01-01' }, -1)).toEqual({
      unit: 'year',
      start: '2025-01-01',
    })
  })

  test('次の期間の始まりが今日以前なら、次へ移れる（境界：今日が次の期間の始まりの日）', () => {
    expect(hasNextRunning({ unit: 'week', start: '2026-11-02' }, TODAY)).toBe(true)
    expect(hasNextRunning({ unit: 'week', start: '2026-11-09' }, TODAY)).toBe(false)
    expect(hasNextRunning({ unit: 'week', start: '2026-11-02' }, '2026-11-09')).toBe(true)
    expect(hasNextRunning({ unit: 'week', start: '2026-11-02' }, '2026-11-08')).toBe(false)
    expect(hasNextRunning({ unit: 'month', start: '2026-10-01' }, TODAY)).toBe(true)
    expect(hasNextRunning({ unit: 'year', start: '2026-01-01' }, TODAY)).toBe(false)
    expect(hasNextRunning({ unit: 'year', start: '2025-01-01' }, TODAY)).toBe(true)
  })
})

describe('期間の表示', () => {
  test('週は「2026/11/09（月）〜 11/15（日）」、月は「2026年11月」、年は「2026年」', () => {
    expect(runningLabel({ unit: 'week', start: '2026-11-09' })).toBe(
      '2026/11/09（月）〜 11/15（日）',
    )
    expect(runningLabel({ unit: 'month', start: '2026-11-01' })).toBe('2026年11月')
    expect(runningLabel({ unit: 'year', start: '2026-01-01' })).toBe('2026年')
  })
})

describe('集計値と前の期間との差', () => {
  test('走行距離・走行時間・回数・平均ペース', () => {
    const items = runningSummaryItems(
      summary({ distanceKm: 45.2, durationSec: 16270, runCount: 8, avgPaceSecPerKm: 360 }),
      summary({ distanceKm: 40.1, durationSec: 14770, runCount: 7, avgPaceSecPerKm: 368 }),
      'km',
    )
    expect(items).toEqual([
      { label: '走行距離', value: '45.20km', diff: { text: '+5.10', sign: 'up' } },
      { label: '走行時間', value: '4:31:10', diff: { text: '+25:00', sign: 'up' } },
      { label: '回数', value: '8回', diff: { text: '+1', sign: 'up' } },
      // ペースは速くなる（秒が減る）ほどよいので、− をよい方の色にする
      { label: '平均ペース', value: '6:00/km', diff: { text: '−0:08', sign: 'up' } },
    ])
  })

  test('減ったとき・同じとき。ペースが遅くなったら＋で、よくない方の色', () => {
    const items = runningSummaryItems(
      summary({ distanceKm: 5, durationSec: 1800, runCount: 1, avgPaceSecPerKm: 360 }),
      summary({ distanceKm: 5, durationSec: 5460, runCount: 2, avgPaceSecPerKm: 359 }),
      'km',
    )
    expect(items.map((item) => item.diff)).toEqual([
      { text: '±0', sign: 'same' },
      { text: '−1:01:00', sign: 'down' },
      { text: '−1', sign: 'down' },
      { text: '+0:01', sign: 'down' },
    ])
  })

  test('ペースが同じなら±0。走行時間の差が 0 なら±0', () => {
    const items = runningSummaryItems(
      summary({ distanceKm: 5, durationSec: 1800, runCount: 1, avgPaceSecPerKm: 360 }),
      summary({ distanceKm: 5, durationSec: 1800, runCount: 1, avgPaceSecPerKm: 360 }),
      'km',
    )
    expect(items[1].diff).toEqual({ text: '±0', sign: 'same' })
    expect(items[3].diff).toEqual({ text: '±0', sign: 'same' })
  })

  test('平均ペースがない（距離のある記録がない）ときは「—」で、差を出さない。前の期間にないときも差を出さない', () => {
    const none = runningSummaryItems(
      summary({ durationSec: 1800, runCount: 1 }),
      summary({ distanceKm: 5, durationSec: 1800, runCount: 1, avgPaceSecPerKm: 360 }),
      'km',
    )
    expect(none[3]).toEqual({ label: '平均ペース', value: '—', diff: null })
    const noPrevious = runningSummaryItems(
      summary({ distanceKm: 5, durationSec: 1800, runCount: 1, avgPaceSecPerKm: 360 }),
      summary(),
      'km',
    )
    expect(noPrevious[3]).toEqual({ label: '平均ペース', value: '6:00/km', diff: null })
  })

  test('平均ペースは、記録の詳細と同じく、分が 60 を超えてもそのまま出す（「61:40/km」）', () => {
    const items = runningSummaryItems(
      summary({ distanceKm: 1, durationSec: 3700, runCount: 1, avgPaceSecPerKm: 3700 }),
      summary(),
      'km',
    )
    expect(items[3].value).toBe('61:40/km')
  })

  test('マイル表示では、距離はマイル、ペースは1マイルあたり（換算した値どうしで差を出す）', () => {
    const items = runningSummaryItems(
      summary({ distanceKm: 16.09344, durationSec: 3600, runCount: 2, avgPaceSecPerKm: 360 }),
      summary({ distanceKm: 8.04672, durationSec: 1800, runCount: 1, avgPaceSecPerKm: 365 }),
      'mi',
    )
    expect(items[0]).toEqual({
      label: '走行距離',
      value: '10.00mi',
      diff: { text: '+5.00', sign: 'up' },
    })
    // 360 秒/km ＝ 579.36 → 579 秒/mi、365 秒/km ＝ 587.41 → 587 秒/mi
    expect(items[3]).toEqual({
      label: '平均ペース',
      value: '9:39/mi',
      diff: { text: '−0:08', sign: 'up' },
    })
  })
})

describe('ベスト記録', () => {
  test('最速ペースは「5:12/km」と、距離・時間・日付', () => {
    expect(bestPaceText(run(), 'km')).toEqual({
      value: '5:12/km',
      detail: '10.00km・52:00・11/8',
    })
  })

  test('最長距離は「15.00km」と、時間・日付', () => {
    expect(
      longestRunText(run({ performedOn: '2026-11-03', distanceKm: 15, durationSec: 5720 }), 'km'),
    ).toEqual({ value: '15.00km', detail: '1:35:20・11/3' })
  })

  test('マイル表示', () => {
    expect(bestPaceText(run({ distanceKm: 16.09344, durationSec: 4800 }), 'mi')).toEqual({
      value: '8:00/mi',
      detail: '10.00mi・1:20:00・11/8',
    })
    expect(longestRunText(run({ distanceKm: 16.09344, durationSec: 4800 }), 'mi').value).toBe(
      '10.00mi',
    )
  })
})

describe('年間の、月ごとの走行距離', () => {
  test('「1月」〜「12月」と、距離の単位に換算した値（小数第2位まで）', () => {
    const monthly = Array.from({ length: 12 }, (_, i) => ({
      month: `2026-${String(i + 1).padStart(2, '0')}`,
      distanceKm: i === 0 ? 12.345 : i === 10 ? 16.09344 : 0,
    }))
    expect(monthlyDistancePoints(monthly, 'km')[0]).toEqual({ label: '1月', value: 12.35 })
    expect(monthlyDistancePoints(monthly, 'km')[11]).toEqual({ label: '12月', value: 0 })
    expect(monthlyDistancePoints(monthly, 'mi')[10]).toEqual({ label: '11月', value: 10 })
  })
})
