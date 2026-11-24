/** 数えた件数をそのまま出す上限。これより多いと「99+件」 */
const MAX_SHOWN = 99

/** 新しい投稿のお知らせのボタンの文言（docs/screens.md S-10、F-24）。0件なら出さない（null） */
export function newPostsLabel(count: number): string | null {
  if (count <= 0) return null
  return `↑ ${count > MAX_SHOWN ? `${MAX_SHOWN}+` : count}件の新しい投稿`
}
