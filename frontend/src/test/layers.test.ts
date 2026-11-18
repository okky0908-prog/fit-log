import { expect, test } from 'vitest'

// アプリのファイルが、どれかの層のフォルダーに入っていることを確かめる（docs/test-plan.md 6章。バックエンドの LayerPackageTest と同じ役目）。
// カバレッジの基準（vite.config.ts の coverage.thresholds）は、フォルダーのパターンで層ごとに分けている。
// 層の外に置いたファイルは基準の対象から漏れるので、ここで止める。
// 層を足すとき（例：画面の切り替えを置く router）は、この表とテスト計画書 6章の表を同じ PR で直す

/** 層：関数・通信（src/lib）・部品（src/components）・画面（src/pages） */
const LAYERED = /^\/src\/(lib|components|pages)\//

/** 層に入れないファイル：アプリの入口・テストそのもの・テスト用の共通ファイル・型だけのファイル */
const EXCLUDED = /^\/src\/main\.tsx$|\.test\.tsx?$|^\/src\/test\/|\.d\.ts$/

test('アプリのファイルはどれかの層のフォルダーに入っている', () => {
  // 中身は読み込まず、ファイルの名前だけを集める
  const files = Object.keys(import.meta.glob('/src/**/*.{ts,tsx}'))

  expect(files).not.toHaveLength(0)
  const outside = files.filter((file) => !EXCLUDED.test(file) && !LAYERED.test(file))
  expect(outside, '層（src/lib・src/components・src/pages）の外に置いたファイル').toEqual([])
})
