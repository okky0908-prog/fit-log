import type { Visibility } from '../lib/api/types'

/** 絵文字のアイコン。絵文字は読み上げずに、意味（label）を読み上げる。マウスを乗せると意味を出す */
export function EmojiIcon({ emoji, label }: { emoji: string; label: string }) {
  return (
    <span title={label}>
      <span aria-hidden="true">{emoji}</span>
      <span className="visually-hidden">{label}</span>
    </span>
  )
}

const VISIBILITY: Record<Visibility, { icon: string; label: string }> = {
  public: { icon: '🌐', label: '公開' },
  followers: { icon: '👥', label: 'フォロワー限定' },
  private: { icon: '🔒', label: '自分のみ' },
}

/** 公開範囲のアイコン（🌐 公開／👥 フォロワー限定／🔒 自分のみ） */
export function VisibilityIcon({ visibility }: { visibility: Visibility }) {
  const { icon, label } = VISIBILITY[visibility]
  return <EmojiIcon emoji={icon} label={label} />
}
