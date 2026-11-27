/*
 * 期間の切り替え（docs/screens.md「期間の切り替え（共通）」）。
 * 日付は YYYY-MM-DD、月は YYYY-MM の文字で扱う。週は月曜始まり。「今日」は日本時間（呼ぶ側が tokyoDate で渡す）。
 * 端末のタイムゾーンに左右されないよう、暦の計算は UTC の日付で行う
 */

const WEEKDAYS = ['日', '月', '火', '水', '木', '金', '土']

const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/
const MONTH_PATTERN = /^\d{4}-\d{2}$/

/** その日を含む週の月曜 */
export function weekStartOf(date: string): string {
  const day = toUtc(date)
  // 日曜（0）は6日前、それ以外は（曜日 − 1）日前が月曜
  const back = (day.getUTCDay() + 6) % 7
  return addDays(date, -back)
}

/** 日を足す（負の数なら引く） */
export function addDays(date: string, days: number): string {
  const day = toUtc(date)
  day.setUTCDate(day.getUTCDate() + days)
  return day.toISOString().slice(0, 10)
}

/** 月を足す（負の数なら引く） */
export function addMonths(month: string, months: number): string {
  const [year, monthNumber] = month.split('-').map(Number)
  const index = year * 12 + (monthNumber - 1) + months
  return `${Math.floor(index / 12)}-${twoDigits((index % 12) + 1)}`
}

/** URL の `?week=` から、表示する週の月曜。なければ・正しい日付でなければ・未来の週なら、今週 */
export function weekFromParam(value: string | null, today: string): string {
  const thisWeek = weekStartOf(today)
  if (value === null || !isDate(value)) {
    return thisWeek
  }
  const monday = weekStartOf(value)
  return monday > thisWeek ? thisWeek : monday
}

/** URL の `?month=` から、表示する月。なければ・正しい月でなければ・未来の月なら、今月 */
export function monthFromParam(value: string | null, today: string): string {
  const thisMonth = today.slice(0, 7)
  if (value === null || !MONTH_PATTERN.test(value)) {
    return thisMonth
  }
  const monthNumber = Number(value.slice(5, 7))
  if (monthNumber < 1 || monthNumber > 12) {
    return thisMonth
  }
  return value > thisMonth ? thisMonth : value
}

/** 次の週へ移れるか（次の週の月曜が今日以前） */
export function hasNextWeek(monday: string, today: string): boolean {
  return addDays(monday, 7) <= today
}

/** 次の月へ移れるか（次の月が今月以前） */
export function hasNextMonth(month: string, today: string): boolean {
  return addMonths(month, 1) <= today.slice(0, 7)
}

/** 「2026/11/09（月）〜 11/15（日）」。年をまたぐ週は、終わりの日にも年を付ける */
export function weekLabel(monday: string): string {
  const sunday = addDays(monday, 6)
  const end = sunday.slice(0, 4) === monday.slice(0, 4) ? sunday.slice(5) : sunday
  return `${slashed(monday)}（${weekday(monday)}）〜 ${slashed(end)}（${weekday(sunday)}）`
}

/** 「2026年11月」 */
export function monthLabel(month: string): string {
  return `${month.slice(0, 4)}年${Number(month.slice(5, 7))}月`
}

/** 正しい日付か（2026-02-30 のような、ない日は false） */
export function isDate(value: string): boolean {
  if (!DATE_PATTERN.test(value)) {
    return false
  }
  // 2026-13-01 は不正な日時（NaN）になり、2026-02-30 は 03-02 にずれる
  const day = toUtc(value)
  return !Number.isNaN(day.getTime()) && day.toISOString().slice(0, 10) === value
}

function toUtc(date: string): Date {
  return new Date(`${date}T00:00:00Z`)
}

/** 曜日（「月」） */
export function weekday(date: string): string {
  return WEEKDAYS[toUtc(date).getUTCDay()]
}

function slashed(date: string): string {
  return date.replaceAll('-', '/')
}

function twoDigits(value: number): string {
  return String(value).padStart(2, '0')
}
