import { useMutation } from '@tanstack/react-query'
import { useState } from 'react'
import { useNavigate } from 'react-router'
import { deleteWorkout, errorMessage } from '../lib/api/client'
import { useToast } from '../lib/toast'
import { ConfirmDialog } from './ConfirmDialog'
import styles from './WorkoutOwnerMenu.module.css'

interface Props {
  workoutId: number
  /** 削除したあと（お知らせは出し終えている） */
  onDeleted: () => void
}

/**
 * 自分の記録の `…` メニュー（docs/screens.md「投稿カード」）。「編集」と、確認してからの「削除」。
 * 「複製」「テンプレートとして保存」は段階 4 で足す
 */
export function WorkoutOwnerMenu({ workoutId, onDeleted }: Props) {
  const [open, setOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const navigate = useNavigate()
  const toast = useToast()
  const removal = useMutation({
    mutationFn: () => deleteWorkout(workoutId),
    onSuccess: () => {
      setConfirming(false)
      toast('記録を削除しました')
      onDeleted()
    },
    onError: (err) => {
      setConfirming(false)
      toast(errorMessage(err))
    },
  })

  return (
    <div className={styles.menu}>
      <button
        type="button"
        className={styles.menuButton}
        aria-label="メニュー"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        …
      </button>
      {open && (
        <ul role="menu" className={styles.menuList}>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              className={styles.menuItemPlain}
              onClick={() => void navigate(`/workouts/${workoutId}/edit`)}
            >
              編集
            </button>
          </li>
          <li role="none">
            <button
              type="button"
              role="menuitem"
              className={styles.menuItem}
              onClick={() => {
                setOpen(false)
                setConfirming(true)
              }}
            >
              削除
            </button>
          </li>
        </ul>
      )}
      {confirming && (
        <ConfirmDialog
          title="この記録を削除しますか？"
          description="種目・セット・画像・コメントも削除されます。元に戻せません"
          confirmLabel="削除する"
          busy={removal.isPending}
          onConfirm={() => removal.mutate()}
          onCancel={() => setConfirming(false)}
        />
      )}
    </div>
  )
}
