import { act, render, screen } from '@testing-library/react'
import { useEffect } from 'react'
import { afterEach, beforeEach, expect, test, vi } from 'vitest'
import { useToast } from '../lib/toast'
import { ToastProvider } from './Toast'

// お知らせ（docs/screens.md「確認ダイアログ・お知らせ」）：画面の下に短いお知らせを3秒出す

let show: (message: string) => void

function Probe() {
  const toast = useToast()
  useEffect(() => {
    show = toast
  })
  return null
}

beforeEach(() => {
  vi.useFakeTimers()
  render(
    <ToastProvider>
      <Probe />
    </ToastProvider>,
  )
})

afterEach(() => {
  vi.useRealTimers()
})

test('出したお知らせは、読み上げにも伝わる場所に出る', () => {
  act(() => show('記録を投稿しました'))

  expect(screen.getByRole('status')).toHaveTextContent('記録を投稿しました')
})

test('3秒たつと消える（その直前までは出ている）', () => {
  act(() => show('記録を投稿しました'))

  act(() => vi.advanceTimersByTime(2999))
  expect(screen.getByRole('status')).toHaveTextContent('記録を投稿しました')

  act(() => vi.advanceTimersByTime(1))
  expect(screen.getByRole('status')).toBeEmptyDOMElement()
})

test('続けて出したら新しいお知らせに替え、そこから3秒出す', () => {
  act(() => show('1つ目'))
  act(() => vi.advanceTimersByTime(2000))
  act(() => show('2つ目'))

  act(() => vi.advanceTimersByTime(2000))
  expect(screen.getByRole('status')).toHaveTextContent('2つ目')

  act(() => vi.advanceTimersByTime(1000))
  expect(screen.getByRole('status')).toBeEmptyDOMElement()
})

test('ToastProvider の外で useToast を使うとエラー', () => {
  vi.spyOn(console, 'error').mockImplementation(() => {})
  expect(() => render(<Probe />)).toThrow('useToast は ToastProvider の中で使ってください')
})
