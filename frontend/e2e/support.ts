// E2E テストで共通して使うもの（ユーザーを作る・API で下準備する・ログインする・メールを読む・アクセシビリティの確認）
import AxeBuilder from '@axe-core/playwright'
import { type APIRequestContext, expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'

export interface TestUser {
  username: string
  displayName: string
  email: string
  password: string
}

/**
 * 毎回違うユーザー（DB はテストの実行全体で共有しているので、名前が重ならないようにする）。
 * ユーザー名は半角英数字と _ で4〜15文字
 */
export function newUser(label: string): TestUser {
  const id = `${Date.now().toString(36).slice(-5)}${Math.floor(Math.random() * 1296)
    .toString(36)
    .padStart(2, '0')}`
  return {
    username: `${label.slice(0, 7)}_${id}`,
    displayName: `${label}さん`,
    email: `${label}_${id}@example.com`,
    password: 'password123',
  }
}

function auth(token: string) {
  return { Authorization: `Bearer ${token}` }
}

/** 画面を通さずに、API でユーザーを登録する（その流れの本題ではない下準備）。アクセストークンを返す */
export async function signupByApi(request: APIRequestContext, user: TestUser): Promise<string> {
  const res = await request.post('/api/auth/signup', {
    data: {
      username: user.username,
      displayName: user.displayName,
      email: user.email,
      password: user.password,
    },
  })
  expect(res.status(), await res.text()).toBe(201)
  return ((await res.json()) as { accessToken: string }).accessToken
}

/** API で記録を作る（下準備）。腕立て伏せ（ID 8）20回 × 1セット。作った記録の ID を返す */
export async function workoutByApi(
  request: APIRequestContext,
  token: string,
  body: string,
): Promise<number> {
  const today = new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Tokyo' }).format(new Date())
  const data = {
    performedOn: today,
    body,
    durationMin: null,
    visibility: 'public',
    weightUnit: 'kg',
    distanceUnit: 'km',
    exercises: [{ exerciseId: 8, sets: [{ reps: 20 }] }],
  }
  const res = await request.post('/api/workouts', {
    headers: auth(token),
    multipart: {
      data: {
        name: 'data.json',
        mimeType: 'application/json',
        buffer: Buffer.from(JSON.stringify(data)),
      },
    },
  })
  expect(res.status(), await res.text()).toBe(201)
  return ((await res.json()) as { id: number }).id
}

/**
 * ログイン画面からログインし、ログイン画面から移るのを待つ。移る先は、ホームか、ログアウトする前に開いていた画面
 * （アプリは、ログインのあとにもとの画面へ戻す）
 */
export async function login(page: Page, user: TestUser): Promise<void> {
  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill(user.password)
  await page.getByRole('button', { name: 'ログイン' }).click()
  await expect(page).not.toHaveURL(/\/login$/)
}

/** ログアウトする（アイコンのメニュー） */
export async function logout(page: Page): Promise<void> {
  await page.getByRole('button', { name: '自分のメニュー' }).click()
  await page.getByRole('menuitem', { name: 'ログアウト' }).click()
  await expect(page).toHaveURL(/\/login$/)
}

/**
 * 使い捨ての Mailpit の API の URL。バックエンド（TestFitLogApplication）が起動したときに書き出したファイルから読む
 * （ポートは起動のたびに変わる）
 */
function mailpitUrl(): string {
  const file = fileURLToPath(new URL('../../backend/build/e2e/mailpit-url.txt', import.meta.url))
  return readFileSync(file, 'utf8').trim()
}

/** その宛先に届いた一番新しいメールの本文から、確認の画面へのリンクを取り出す。届くまで待つ */
export async function verificationLinkFor(
  request: APIRequestContext,
  email: string,
): Promise<string> {
  let link: string | undefined
  await expect
    .poll(
      async () => {
        const query = encodeURIComponent(`to:"${email}"`)
        const search = await request.get(`${mailpitUrl()}/api/v1/search?query=${query}`)
        const messages = ((await search.json()) as { messages: { ID: string }[] }).messages
        if (messages.length === 0) return undefined
        const message = await request.get(`${mailpitUrl()}/api/v1/message/${messages[0].ID}`)
        const text = ((await message.json()) as { Text: string }).Text
        link = text.match(/http:\/\/localhost:5173\/verify-email\?token=[\w-]+/)?.[0]
        return link
      },
      { timeout: 10_000, message: `${email} に確認メールが届くのを待つ` },
    )
    .toBeDefined()
  return link as string
}

/**
 * 画面にアクセシビリティの違反（axe。WCAG 2.1 AA）がないことを確かめる（docs/test-plan.md 8章：画面を作る段階から axe を入れる）。
 * 違反があれば、どの規則のどの要素かを失敗の内容に出す。画面の要素が出るのを待ってから呼ぶ
 */
export async function expectNoA11yViolations(page: Page): Promise<void> {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const violations = results.violations.map((v) => ({
    id: v.id,
    help: v.help,
    targets: v.nodes.map((node) => node.target.join(' ')),
  }))
  expect(violations).toEqual([])
}
