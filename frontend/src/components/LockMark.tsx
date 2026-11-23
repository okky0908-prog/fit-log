/** 鍵アカウントの印（docs/screens.md「ユーザーへのリンク（共通）」）。表示名の横に付ける。読み上げでは「鍵アカウント」と読む */
export function LockMark() {
  return (
    <span title="鍵アカウント">
      <span aria-hidden="true">🔒</span>
      <span className="visually-hidden">鍵アカウント</span>
    </span>
  )
}
