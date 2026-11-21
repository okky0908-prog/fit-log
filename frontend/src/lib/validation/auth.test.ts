import { describe, expect, test } from 'vitest'
import { type SignupForm, validateLogin, validateSignup } from './auth'

// 入力チェック（docs/test-plan.md 5.1 の境界値）。画面のチェックは使いやすさのためで、サーバーでも同じチェックをする。
// 文言はサーバー（SignupRequest）と同じにする

const VALID: SignupForm = {
  username: 'yamada',
  displayName: '山田太郎',
  email: 'yamada@example.com',
  password: 'password123',
  passwordConfirm: 'password123',
}

function errorsFor(change: Partial<SignupForm>) {
  return validateSignup({ ...VALID, ...change })
}

describe('新規登録（S-02）', () => {
  test('すべて正しければエラーはない', () => {
    expect(validateSignup(VALID)).toEqual({})
  })

  test.each([
    ['3文字', 'abc', false],
    ['4文字', 'abcd', true],
    ['15文字', 'a'.repeat(15), true],
    ['16文字', 'a'.repeat(16), false],
    ['_ と数字', 'user_01', true],
    ['全角', 'ｙａｍａｄａ', false],
    ['記号', 'yama-da', false],
    ['空', '', false],
  ])('ユーザー名：%s', (_, username, valid) => {
    expect(errorsFor({ username }).username).toBe(
      valid ? undefined : 'ユーザー名は半角英数字と_で4〜15文字で入力してください',
    )
  })

  test.each([
    ['0文字', '', false],
    ['空白だけ', '　 ', false],
    ['1文字', 'a', true],
    ['50文字', 'あ'.repeat(50), true],
    ['51文字', 'あ'.repeat(51), false],
    ['絵文字50個（1つを1文字と数える）', '💪'.repeat(50), true],
    ['前後の空白は数えない', ` ${'あ'.repeat(50)} `, true],
  ])('表示名：%s', (_, displayName, valid) => {
    expect(errorsFor({ displayName }).displayName).toBe(
      valid ? undefined : '表示名は1〜50文字で入力してください',
    )
  })

  test.each([
    ['正しい形', 'yamada@example.com', true],
    ['@ がない', 'yamada.example.com', false],
    ['ドメインに . がない', 'yamada@example', false],
    ['空', '', false],
    ['前後の空白は除いて確かめる', ' yamada@example.com ', true],
  ])('メールアドレス：%s', (_, email, valid) => {
    expect(errorsFor({ email }).email).toBe(
      valid ? undefined : 'メールアドレスの形式が正しくありません',
    )
  })

  test.each([
    ['7文字', 'a'.repeat(7), 'パスワードは8文字以上で入力してください'],
    ['8文字', 'a'.repeat(8), undefined],
    ['72文字', 'a'.repeat(72), undefined],
    ['73文字', 'a'.repeat(73), 'パスワードは72文字以内で入力してください'],
    ['日本語24文字（72バイト）', 'あ'.repeat(24), undefined],
    [
      '日本語25文字（75バイト）',
      'あ'.repeat(25),
      'パスワードが長すぎます（全角文字は1文字を3バイトとして、72バイトまで）',
    ],
  ])('パスワード：%s', (_, password, message) => {
    expect(errorsFor({ password, passwordConfirm: password }).password).toBe(message)
  })

  test('パスワード（確認）が違えばエラー', () => {
    expect(errorsFor({ passwordConfirm: 'password124' }).passwordConfirm).toBe(
      'パスワードが一致しません',
    )
  })
})

describe('ログイン（S-01）', () => {
  test('空でなければエラーはない', () => {
    expect(validateLogin({ email: 'a', password: 'b' })).toEqual({})
  })

  test('メールアドレス（空白だけを含む）とパスワードが空ならエラー', () => {
    expect(validateLogin({ email: ' ', password: '' })).toEqual({
      email: 'メールアドレスを入力してください',
      password: 'パスワードを入力してください',
    })
  })
})
