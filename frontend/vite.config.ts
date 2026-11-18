/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    // 開発時は、/api を Spring Boot（8080）に中継する（docs/tech-stack.md）
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  // テスト（Vitest。npm test）
  test: {
    // 日付の表示は実行する場所のタイムゾーンで変わるので、利用者と同じ日本時間に固定する
    // （CI は UTC で動くため、固定しないと手元と結果がずれる。docs/test-plan.md 3.2）
    env: { TZ: 'Asia/Tokyo' },
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    // カバレッジ（npm run test:coverage。docs/test-plan.md 6章）
    coverage: {
      provider: 'v8',
      // テストのないファイルも 0% として数えるよう、src の下をすべて対象にする
      include: ['src/**/*.{ts,tsx}'],
      // テストそのもの・テスト用の共通ファイル・起動の入口は数えない
      exclude: ['src/**/*.test.{ts,tsx}', 'src/test/**', 'src/main.tsx', 'src/**/*.d.ts'],
      reporter: ['text', 'html', 'json-summary'],
      // 層ごとの基準。関数・通信（src/lib）は、ファイルごとに分岐 90% を下回ったら落とす。
      // 部品・画面（src/components・src/pages）は目安（80%）として表で見るだけにし、落とさない。
      // どのファイルも層のフォルダーに入っていることは、src/test/layers.test.ts が確かめる
      thresholds: {
        'src/lib/**': { branches: 90, perFile: true },
      },
    },
  },
})
