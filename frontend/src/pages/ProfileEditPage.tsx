import { useQueryClient } from '@tanstack/react-query'
import { type ChangeEvent, type FormEvent, useEffect, useId, useState } from 'react'
import { useNavigate } from 'react-router'
import { Avatar } from '../components/Avatar'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FormField } from '../components/FormField'
import { ApiError, errorMessage, updateProfile } from '../lib/api/client'
import type { Me } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import {
  ICON_MAX_SIDE,
  shrinkImage,
  UNUSABLE_IMAGE_MESSAGE,
  UnusableImageError,
} from '../lib/image/shrink'
import { useToast } from '../lib/toast'
import { BIO_MAX_LENGTH, bioLength, validateProfile } from '../lib/validation/profile'
import styles from './ProfileEditPage.module.css'

type Errors = Partial<Record<'displayName' | 'bio' | 'icon', string>>

const FIELDS = new Set(['displayName', 'bio', 'icon'])

/**
 * S-41 プロフィール編集（docs/screens.md）。表示名・自己紹介・アイコンを変える。`@ユーザー名` とメールアドレスはここでは変えられない。
 * アイコンはブラウザで 512px 四方に収まるよう縮小してから送る。保存したら自分の S-40 へ戻り、お知らせを出す。
 * キャンセルは、変更があれば確かめてから戻る
 */
export function ProfileEditPage() {
  const { user } = useAuth()
  // ログインが必要な画面なので、user は必ずある
  return user ? <ProfileForm user={user} /> : null
}

function ProfileForm({ user }: { user: Me }) {
  const { reloadUser } = useAuth()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const bioId = useId()
  const [displayName, setDisplayName] = useState(user.displayName)
  const [bio, setBio] = useState(user.bio ?? '')
  const [icon, setIcon] = useState<{ blob: Blob; url: string } | null>(null)
  const [errors, setErrors] = useState<Errors>({})
  const [failure, setFailure] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [confirmingCancel, setConfirmingCancel] = useState(false)
  const profilePath = `/users/${encodeURIComponent(user.username)}`
  const changed = displayName !== user.displayName || bio !== (user.bio ?? '') || icon !== null

  // 見本の仮の URL は、替えたとき・画面を離れるときに捨てる
  useEffect(() => () => (icon ? URL.revokeObjectURL(icon.url) : undefined), [icon])

  async function chooseIcon(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    try {
      const blob = await shrinkImage(file, undefined, ICON_MAX_SIDE)
      setIcon({ blob, url: URL.createObjectURL(blob) })
      setErrors((current) => ({ ...current, icon: undefined }))
    } catch (err) {
      if (!(err instanceof UnusableImageError)) throw err
      setErrors((current) => ({ ...current, icon: UNUSABLE_IMAGE_MESSAGE }))
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    const found = validateProfile({ displayName, bio })
    setErrors(found)
    setFailure(null)
    if (Object.keys(found).length > 0) return
    setSaving(true)
    try {
      await updateProfile({ displayName, bio }, icon?.blob)
      await reloadUser()
      // 前に開いたプロフィール・一覧に、古い名前やアイコンを残さない
      void queryClient.invalidateQueries({ queryKey: ['users'] })
      toast('プロフィールを更新しました')
      void navigate(profilePath)
    } catch (err) {
      // 項目に結び付く誤り（アイコンの位置情報など）はその項目の下に、それ以外は上に出す
      const known =
        err instanceof ApiError ? err.fieldErrors.filter((e) => FIELDS.has(e.field)) : []
      if (known.length > 0) {
        setErrors(Object.fromEntries(known.map((e) => [e.field, e.message])))
      } else {
        setFailure(errorMessage(err))
      }
      setSaving(false)
    }
  }

  function cancel() {
    if (changed) {
      setConfirmingCancel(true)
    } else {
      void navigate(profilePath)
    }
  }

  return (
    <>
      <h1 className={styles.title}>プロフィールを編集</h1>
      <form className={styles.form} onSubmit={(e) => void submit(e)} noValidate>
        {failure && <p className={styles.failure}>{failure}</p>}
        <div className={styles.icon}>
          <Avatar
            displayName={displayName || user.displayName}
            iconUrl={icon?.url ?? user.iconUrl}
            size={80}
          />
          <div>
            <label className={`button-secondary ${styles.choose}`}>
              アイコン画像を変更
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                className="visually-hidden"
                aria-label="アイコン画像を選ぶ"
                aria-invalid={errors.icon ? true : undefined}
                onChange={(e) => void chooseIcon(e)}
              />
            </label>
            <p className={styles.note}>JPEG・PNG・WebP、20MBまで。保存するまでは反映されません</p>
            {errors.icon && <p className={styles.error}>{errors.icon}</p>}
          </div>
        </div>
        <FormField
          label="表示名"
          value={displayName}
          maxLength={50}
          error={errors.displayName}
          onChange={(e) => setDisplayName(e.target.value)}
        />
        <div className={styles.field}>
          <label htmlFor={bioId} className={styles.label}>
            自己紹介
          </label>
          <textarea
            id={bioId}
            className={styles.textarea}
            rows={4}
            value={bio}
            aria-invalid={errors.bio ? true : undefined}
            aria-describedby={`${bioId}-count`}
            onChange={(e) => setBio(e.target.value)}
          />
          <p id={`${bioId}-count`} className={styles.count}>
            {bioLength(bio)} / {BIO_MAX_LENGTH}
          </p>
          {errors.bio && <p className={styles.error}>{errors.bio}</p>}
        </div>
        <p className={styles.note}>
          @{user.username}（ユーザー名とメールアドレスは、ここでは変更できません）
        </p>
        <div className={styles.actions}>
          <button type="button" className="button-secondary" onClick={cancel}>
            キャンセル
          </button>
          <button type="submit" className="button-primary" disabled={saving}>
            保存する
          </button>
        </div>
      </form>
      {confirmingCancel && (
        <ConfirmDialog
          title="変更は保存されません。よろしいですか？"
          confirmLabel="戻る"
          onConfirm={() => void navigate(profilePath)}
          onCancel={() => setConfirmingCancel(false)}
        />
      )}
    </>
  )
}
