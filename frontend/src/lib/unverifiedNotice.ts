// 未確認の案内（docs/screens.md「ナビゲーション」）。閉じるボタンで、その日（日本時間）は出さない。
// 閉じた日はブラウザ（localStorage）に覚える。使えないとき（プライベートブラウズなど）は、閉じても覚えられないだけにする

const KEY = 'fitlog.unverifiedNotice.dismissedOn'

const TOKYO_DATE = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Tokyo',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
})

/** 日本時間の日付（YYYY-MM-DD）。利用者の端末のタイムゾーンによらない */
export function tokyoDate(now: Date): string {
  return TOKYO_DATE.format(now)
}

/** 今日（日本時間）は閉じたか */
export function isNoticeDismissed(storage: Storage, now: Date): boolean {
  try {
    return storage.getItem(KEY) === tokyoDate(now)
  } catch {
    return false
  }
}

/** 今日（日本時間）は出さないようにする */
export function dismissNotice(storage: Storage, now: Date): void {
  try {
    storage.setItem(KEY, tokyoDate(now))
  } catch {
    // 覚えられないだけ。閉じた表示は画面の状態で行う
  }
}
