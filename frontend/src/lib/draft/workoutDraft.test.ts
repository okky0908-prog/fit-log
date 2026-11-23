import { beforeEach, describe, expect, test } from 'vitest'
import type { WorkoutForm } from '../validation/workout'
import { clearDraft, loadDraft, saveDraft } from './workoutDraft'

// 書きかけの記録の一時保存（docs/screens.md S-13「入力中の内容の一時保存」）

const FORM: WorkoutForm = {
  performedOn: '2026-11-22',
  durationMin: '45',
  body: '書きかけ',
  visibility: 'followers',
  exercises: [
    {
      exerciseId: 1,
      name: 'ベンチプレス',
      bodyPart: 'chest',
      recordType: 'weight_reps',
      isRunning: false,
      sets: [{ weight: '60', reps: '10', duration: '', distance: '' }],
    },
  ],
}

/** 保存できない端末（プライベートモード・容量いっぱい・使えない設定）の代わり */
const BROKEN_STORAGE = {
  getItem: () => {
    throw new Error('SecurityError')
  },
  setItem: () => {
    throw new Error('QuotaExceededError')
  },
  removeItem: () => {
    throw new Error('SecurityError')
  },
} as unknown as Storage

beforeEach(() => {
  localStorage.clear()
})

describe('保存と読み込み', () => {
  test('保存した内容をそのまま読み込める', () => {
    saveDraft('yamada', FORM)

    expect(loadDraft('yamada')).toEqual(FORM)
  })

  test('ユーザーごとに分ける（同じ端末の別のアカウントには出さない）', () => {
    saveDraft('yamada', FORM)

    expect(loadDraft('sato')).toBeNull()
  })

  test('消すと、読み込めなくなる', () => {
    saveDraft('yamada', FORM)

    clearDraft('yamada')

    expect(loadDraft('yamada')).toBeNull()
  })

  test('保存していなければ null', () => {
    expect(loadDraft('yamada')).toBeNull()
  })
})

describe('壊れた中身・保存できない端末', () => {
  test('読めない中身は null にして、捨てる', () => {
    localStorage.setItem('fitlog:workout-draft:yamada', '{壊れた')

    expect(loadDraft('yamada')).toBeNull()
    expect(localStorage.getItem('fitlog:workout-draft:yamada')).toBeNull()
  })

  test.each([
    ['null', 'null'],
    ['文字', '"文字"'],
    ['種目が配列でない', JSON.stringify({ ...FORM, exercises: 'なし' })],
    ['実施日が文字でない', JSON.stringify({ ...FORM, performedOn: 20261122 })],
    ['公開範囲が知らない値', JSON.stringify({ ...FORM, visibility: 'everyone' })],
    [
      'セットの値が文字でない',
      JSON.stringify({ ...FORM, exercises: [{ ...FORM.exercises[0], sets: [{ reps: 10 }] }] }),
    ],
    [
      '記録方式が知らない値',
      JSON.stringify({ ...FORM, exercises: [{ ...FORM.exercises[0], recordType: 'unknown' }] }),
    ],
    [
      '種目の ID が数でない',
      JSON.stringify({ ...FORM, exercises: [{ ...FORM.exercises[0], exerciseId: '1' }] }),
    ],
    ['種目が null', JSON.stringify({ ...FORM, exercises: [null] })],
  ])('形が違う中身（%s）は null にして、捨てる', (_name, broken) => {
    localStorage.setItem('fitlog:workout-draft:yamada', broken)

    expect(loadDraft('yamada')).toBeNull()
    expect(localStorage.getItem('fitlog:workout-draft:yamada')).toBeNull()
  })

  test('保存できない端末でも、エラーにせず何もしない', () => {
    expect(() => saveDraft('yamada', FORM, BROKEN_STORAGE)).not.toThrow()
    expect(loadDraft('yamada', BROKEN_STORAGE)).toBeNull()
    expect(() => clearDraft('yamada', BROKEN_STORAGE)).not.toThrow()
  })
})
