import { expect, test } from '@playwright/test'
import { expectNoA11yViolations, login, logout, newUser, verificationLinkFor } from './support.ts'

// 段階 1 の E2E：登録・ログイン、届いた確認メールのリンクでメールアドレスを確認する（docs/test-plan.md 8章、UC-01〜UC-03）

test('新規登録し、届いた確認メールのリンクで確認して、ログアウト・ログインできる', async ({
  page,
  request,
}) => {
  const user = newUser('signup')

  await page.goto('/signup')
  await expect(page.getByRole('heading', { level: 1, name: '新規登録' })).toBeVisible()
  await expectNoA11yViolations(page)
  await page.getByLabel('ユーザー名').fill(user.username)
  await page.getByLabel('表示名').fill(user.displayName)
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード', { exact: true }).fill(user.password)
  await page.getByLabel('パスワード（確認）').fill(user.password)
  await page.getByRole('button', { name: '登録する' }).click()

  // 登録するとログインした状態でホームへ移る。メールアドレスが未確認なので案内が出る
  await expect(page).toHaveURL(/\/$/)
  await expect(
    page.getByText('メールアドレスの確認が済んでいません。', { exact: false }),
  ).toBeVisible()
  await expect(page.getByText('まだ記録がありません。最初の記録をつけてみましょう')).toBeVisible()
  await expectNoA11yViolations(page)

  // 届いた確認メールのリンクを開く
  await page.goto(await verificationLinkFor(request, user.email))
  await expect(page.getByText('メールアドレスの確認が済みました')).toBeVisible()
  await expectNoA11yViolations(page)

  // ログアウトして、ログインし直すと、案内はもう出ない
  await page.goto('/')
  await logout(page)
  await login(page, user)
  await expect(page).toHaveURL(/\/$/)
  await expect(page.getByText('まだ記録がありません。最初の記録をつけてみましょう')).toBeVisible()
  await expect(
    page.getByText('メールアドレスの確認が済んでいません。', { exact: false }),
  ).toHaveCount(0)
})

test('パスワードが違えば、ログインできずに理由を出す', async ({ page, request }) => {
  const user = newUser('badpass')
  await request.post('/api/auth/signup', {
    data: {
      username: user.username,
      displayName: user.displayName,
      email: user.email,
      password: user.password,
    },
  })

  await page.goto('/login')
  await page.getByLabel('メールアドレス').fill(user.email)
  await page.getByLabel('パスワード').fill('wrong-password')
  await page.getByRole('button', { name: 'ログイン' }).click()

  await expect(page.getByText('メールアドレスまたはパスワードが正しくありません')).toBeVisible()
  await expect(page).toHaveURL(/\/login$/)
})
