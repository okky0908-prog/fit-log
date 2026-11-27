import { addDays, isDate, weekday, weekStartOf } from './period'

/*
 * 記録の画面のカレンダー（docs/screens.md S-20、F-82）。月曜始まり。
 * 日付は YYYY-MM-DD、月は YYYY-MM の文字で扱う（period.ts と同じ）
 */

/** カレンダーのます。1日の曜日までを空け（null）、1日から末日までの日付を並べる */
export function calendarCells(month: string): (string | null)[] {
  const first = `${month}-01`
  const blanks =
    (Date.parse(`${first}T00:00:00Z`) - Date.parse(`${weekStartOf(first)}T00:00:00Z`)) / 86_400_000
  const cells: (string | null)[] = Array.from({ length: blanks }, () => null)
  for (let date = first; date.startsWith(month); date = addDays(date, 1)) {
    cells.push(date)
  }
  return cells
}

/** URL の `?day=` から、記録を出す日。表示中の月の、今日までの正しい日付だけ（それ以外は選ばない） */
export function dayFromParam(value: string | null, month: string, today: string): string | null {
  if (value === null || !isDate(value) || !value.startsWith(`${month}-`) || value > today) {
    return null
  }
  return value
}

/** 日付の読み上げの名前（「11月4日（水）」） */
export function dayLabel(date: string): string {
  return `${Number(date.slice(5, 7))}月${Number(date.slice(8, 10))}日（${weekday(date)}）`
}
