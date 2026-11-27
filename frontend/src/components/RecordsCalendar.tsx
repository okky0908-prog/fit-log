import { Link } from 'react-router'
import { calendarCells, dayLabel } from '../lib/stats/calendar'
import { addMonths, hasNextMonth, monthLabel } from '../lib/stats/period'
import { PeriodSwitch } from './PeriodSwitch'
import styles from './RecordsCalendar.module.css'

const WEEKDAYS = ['月', '火', '水', '木', '金', '土', '日']

interface Props {
  /** 表示する月（YYYY-MM） */
  month: string
  /** 今日（日本時間） */
  today: string
  /** 記録がある日。読み込み中は空 */
  dates: Set<string>
  /** 記録を出している日。なければ null */
  selected: string | null
  /** カレンダーの月と、記録を出す日の URL（日を選ばないなら null） */
  hrefFor: (month: string, day: string | null) => string
}

/**
 * 記録の画面のカレンダー（docs/screens.md S-20、F-82）。記録した日に ● を付け、日付を押すとその日の記録を出す。
 * 週間・月間のタブとは別に月を動かせる（未来の月・日には移れない）
 */
export function RecordsCalendar({ month, today, dates, selected, hrefFor }: Props) {
  const next = addMonths(month, 1)
  return (
    <>
      <PeriodSwitch
        label={monthLabel(month)}
        unit="月"
        owner="カレンダー"
        previous={hrefFor(addMonths(month, -1), null)}
        next={hasNextMonth(month, today) ? hrefFor(next, null) : null}
        current={null}
      />
      <div className={styles.calendar}>
        {WEEKDAYS.map((weekday) => (
          <span key={weekday} className={styles.weekday} aria-hidden="true">
            {weekday}
          </span>
        ))}
        {calendarCells(month).map((date, index) =>
          date === null ? (
            <span key={`blank-${index}`} aria-hidden="true" />
          ) : date > today ? (
            // 未来の日は押せない
            <span key={date} className={`${styles.day} ${styles.future}`}>
              {Number(date.slice(8))}
            </span>
          ) : (
            <Link
              key={date}
              to={hrefFor(month, date)}
              replace
              preventScrollReset
              className={`${styles.day} ${date === selected ? styles.selected : ''} ${date === today ? styles.today : ''}`}
              aria-label={`${dayLabel(date)}${dates.has(date) ? ' 記録あり' : ''}`}
              aria-current={date === selected ? 'true' : date === today ? 'date' : undefined}
            >
              {Number(date.slice(8))}
              {dates.has(date) && (
                <span aria-hidden="true" className={styles.dot}>
                  ●
                </span>
              )}
            </Link>
          ),
        )}
      </div>
    </>
  )
}
