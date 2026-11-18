// すべてのテストの前に読み込む（vite.config.ts の test.setupFiles）
// - jest-dom：「表示されているか」などの確認（toBeInTheDocument など）を使えるようにする
// - テストのたびに、描画した部品を片付ける（前のテストの部品が画面に残らないように）
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

afterEach(() => {
  cleanup()
})
