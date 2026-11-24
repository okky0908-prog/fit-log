// すべてのテストの前に読み込む（vite.config.ts の test.setupFiles）
// - jest-dom：「表示されているか」などの確認（toBeInTheDocument など）を使えるようにする
// - テストのたびに、描画した部品を片付ける（前のテストの部品が画面に残らないように）
// - jsdom にない、画像の仮の URL を作る関数（URL.createObjectURL）を偽物で足す。URL は作るたびに違う値にする
// - jsdom にない、ページの位置を動かす関数（window.scrollTo。タイムラインの一番上へ戻すときに使う）を、何もしない偽物で足す
import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'

let objectUrlCount = 0
URL.createObjectURL = () => `blob:test/${++objectUrlCount}`
URL.revokeObjectURL = () => {}
window.scrollTo = () => {}

afterEach(() => {
  cleanup()
})
