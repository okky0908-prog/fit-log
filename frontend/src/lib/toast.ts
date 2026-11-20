import { createContext, useContext } from 'react'

/** お知らせを出す関数（components/Toast.tsx の ToastProvider が用意する） */
export const ToastContext = createContext<((message: string) => void) | null>(null)

/** お知らせを出す関数を返す。画面の移動のあとでも出し続けられるよう、画面の外側（main.tsx）に置いた ToastProvider が出す */
export function useToast(): (message: string) => void {
  const show = useContext(ToastContext)
  if (!show) {
    throw new Error('useToast は ToastProvider の中で使ってください')
  }
  return show
}
