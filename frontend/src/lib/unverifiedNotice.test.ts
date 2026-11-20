import { describe, expect, test } from 'vitest'
import { dismissNotice, isNoticeDismissed, tokyoDate } from './unverifiedNotice'

// 未確認の案内（docs/screens.md「ナビゲーション」）：閉じるボタンで、その日（日本時間）は出さない

/** テスト用の保存先（localStorage と同じ形） */
function memoryStorage(): Storage {
  const values = new Map<string, string>()
  return {
    get length() {
      return values.size
    },
    clear: () => values.clear(),
    getItem: (key) => values.get(key) ?? null,
    key: (index) => [...values.keys()][index] ?? null,
    removeItem: (key) => void values.delete(key),
    setItem: (key, value) => void values.set(key, value),
  }
}

/** 読み書きのたびに失敗する保存先（プライベートブラウズ・保存の禁止） */
function brokenStorage(): Storage {
  const fail = () => {
    throw new Error('使えません')
  }
  return { length: 0, clear: fail, getItem: fail, key: fail, removeItem: fail, setItem: fail }
}

describe('日本時間の日付', () => {
  test.each([
    ['UTC 14:59:59 は日本時間の同じ日の 23:59:59', '2026-11-20T14:59:59Z', '2026-11-20'],
    ['UTC 15:00:00 は日本時間の次の日の 0:00', '2026-11-20T15:00:00Z', '2026-11-21'],
  ])('%s', (_, iso, expected) => {
    expect(tokyoDate(new Date(iso))).toBe(expected)
  })
})

describe('案内を閉じた日', () => {
  test('閉じていなければ出す', () => {
    expect(isNoticeDismissed(memoryStorage(), new Date('2026-11-20T03:00:00Z'))).toBe(false)
  })

  test('閉じたら、その日の終わり（日本時間の 23:59:59）までは出さない', () => {
    const storage = memoryStorage()
    dismissNotice(storage, new Date('2026-11-20T03:00:00Z'))

    expect(isNoticeDismissed(storage, new Date('2026-11-20T14:59:59Z'))).toBe(true)
  })

  test('次の日（日本時間の 0:00）になったら、また出す', () => {
    const storage = memoryStorage()
    dismissNotice(storage, new Date('2026-11-20T03:00:00Z'))

    expect(isNoticeDismissed(storage, new Date('2026-11-20T15:00:00Z'))).toBe(false)
  })

  test('保存先が使えなくても、エラーにせず出す（閉じても覚えられないだけ）', () => {
    const storage = brokenStorage()

    expect(() => dismissNotice(storage, new Date())).not.toThrow()
    expect(isNoticeDismissed(storage, new Date())).toBe(false)
  })
})
