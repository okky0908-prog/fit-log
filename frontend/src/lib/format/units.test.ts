import { describe, expect, test } from 'vitest'
import {
  formatDistance,
  formatDuration,
  formatPace,
  formatPaceValue,
  formatWeight,
  paceSecondsPer,
} from './units'

// 単位の換算と表示の桁（docs/features.md F-63：重量は kg なら 0.25・lb なら 0.5 単位に丸める。距離は小数第2位まで。ペースは m:ss）

describe('重量', () => {
  test('kg は 0.25 単位に丸め、余分な 0 を付けない', () => {
    expect(formatWeight(60, 'kg')).toBe('60kg')
    expect(formatWeight(62.5, 'kg')).toBe('62.5kg')
    expect(formatWeight(0.25, 'kg')).toBe('0.25kg')
    // lb で入力した値（135lb ＝ 61.235kg）を kg で見るとき
    expect(formatWeight(61.235, 'kg')).toBe('61.25kg')
    // 0.25 単位の境目（0.125 の真ん中は切り上げ）
    expect(formatWeight(60.125, 'kg')).toBe('60.25kg')
    expect(formatWeight(60.124, 'kg')).toBe('60kg')
  })

  test('lb は 0.5 単位に丸める_lb で入力した値はそのまま戻る', () => {
    // 135lb で入力 → 61.235kg で保存 → 135lb
    expect(formatWeight(61.235, 'lb')).toBe('135lb')
    // 0.5lb で入力 → 0.227kg で保存 → 0.5lb
    expect(formatWeight(0.227, 'lb')).toBe('0.5lb')
    // kg で入力した 60kg は 132.28lb → 132.5lb
    expect(formatWeight(60, 'lb')).toBe('132.5lb')
    expect(formatWeight(500, 'lb')).toBe('1102.5lb')
  })
})

describe('距離', () => {
  test('小数第2位まで出す', () => {
    expect(formatDistance(5, 'km')).toBe('5.00km')
    expect(formatDistance(0.01, 'km')).toBe('0.01km')
    expect(formatDistance(42.195, 'km')).toBe('42.20km')
    // 2進数では 1.00499… と少し小さく表される値も、四捨五入で切り上げる
    expect(formatDistance(1.005, 'km')).toBe('1.01km')
  })

  test('マイルに換算する_マイルで入力した値はそのまま戻る', () => {
    // 3.1mi で入力 → 4.989km で保存 → 3.10mi
    expect(formatDistance(4.989, 'mi')).toBe('3.10mi')
    expect(formatDistance(1.609, 'mi')).toBe('1.00mi')
  })
})

describe('時間', () => {
  test('1時間未満は m:ss、1時間以上は h:mm:ss', () => {
    expect(formatDuration(1)).toBe('0:01')
    expect(formatDuration(59)).toBe('0:59')
    expect(formatDuration(1800)).toBe('30:00')
    expect(formatDuration(3599)).toBe('59:59')
    expect(formatDuration(3600)).toBe('1:00:00')
    expect(formatDuration(3905)).toBe('1:05:05')
    expect(formatDuration(86400)).toBe('24:00:00')
  })
})

describe('ペース', () => {
  test('1km あたりの m:ss', () => {
    expect(formatPace(1800, 5, 'km')).toBe('6:00/km')
    // 秒は四捨五入（1:59.5 → 2:00）
    expect(formatPace(119.5, 1, 'km')).toBe('2:00/km')
    expect(formatPace(3125, 10, 'km')).toBe('5:13/km')
  })

  test('マイル表示は 1マイルあたり', () => {
    // 1マイル（1.609344km）を 8分
    expect(formatPace(480, 1.609344, 'mi')).toBe('8:00/mi')
  })

  test('1時間以上かかるペースも m:ss のまま分で出す', () => {
    expect(formatPace(3900, 1, 'km')).toBe('65:00/km')
  })
})

describe('集計のペース（1km あたりの秒から）', () => {
  test('マイル表示なら1マイルあたりの秒に換算し、整数に四捨五入する', () => {
    expect(paceSecondsPer(360, 'km')).toBe(360)
    // 360 × 1.609344 ＝ 579.36
    expect(paceSecondsPer(360, 'mi')).toBe(579)
    // 365 × 1.609344 ＝ 587.41
    expect(paceSecondsPer(365, 'mi')).toBe(587)
  })

  test('「6:00/km」。記録の詳細のペースと同じく、分は 60 を超えてもそのまま出す', () => {
    expect(formatPaceValue(360, 'km')).toBe('6:00/km')
    expect(formatPaceValue(579, 'mi')).toBe('9:39/mi')
    expect(formatPaceValue(3700, 'km')).toBe('61:40/km')
  })
})
