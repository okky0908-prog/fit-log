import { useInfiniteQuery, useQuery, useQueryClient } from '@tanstack/react-query'
import { type FormEvent, useId, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { BodyTrendChart } from '../components/BodyTrendChart'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { FormField } from '../components/FormField'
import { LoadMore } from '../components/LoadMore'
import {
  ApiError,
  deleteBodyRecord,
  errorMessage,
  fetchBodyRecords,
  fetchBodyTrend,
  putBodyRecord,
} from '../lib/api/client'
import type { BodyRange, BodyRecord } from '../lib/api/types'
import { useAuth } from '../lib/auth/authContext'
import { tokyoDate } from '../lib/format/date'
import { bodyWeightValue, formatBodyWeight } from '../lib/format/units'
import { bodyTrendPoints } from '../lib/stats/body'
import { useToast } from '../lib/toast'
import { type BodyErrors, toBodyRecordInput, validateBodyRecord } from '../lib/validation/body'
import styles from './BodyPage.module.css'

const RANGES: { range: BodyRange; label: string }[] = [
  { range: '3m', label: '3か月' },
  { range: '1y', label: '1年' },
]

/** 体重の記録・一覧・推移のキャッシュ（保存・削除のあとに読み直す） */
const BODY_KEY = ['body']

/** 項目の下に出すサーバーの誤り（それ以外はお知らせに出す） */
const FIELDS = new Set(['recordedOn', 'weight', 'bodyFatPct'])

/**
 * S-25 体重の記録（/body）（docs/screens.md S-25、F-90〜F-92）。本人だけが見られる。
 * 推移の期間は URL の `?range=1y` に入れる（3か月なら入れない）
 */
export function BodyPage() {
  const { user } = useAuth()
  const unit = user?.weightUnit ?? 'kg'
  // 「今日」（日本時間）。描き直すたびに変わらないよう、開いたときの日付を持つ
  const [today] = useState(() => tokyoDate(new Date()))
  const records = useInfiniteQuery({
    queryKey: [...BODY_KEY, 'records'],
    queryFn: ({ pageParam }) => fetchBodyRecords(pageParam ?? undefined),
    initialPageParam: null as string | null,
    getNextPageParam: (last) => last.nextCursor,
  })
  const items = records.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <>
      <div className={styles.head}>
        <h1 className={styles.title}>体重の記録</h1>
        <p className={styles.private}>🔒 自分だけが見られます</p>
      </div>
      <RecordForm unit={unit} today={today} records={items} />
      <TrendSection unit={unit} />
      <RecordList
        unit={unit}
        records={records.data === undefined ? null : items}
        error={records.error}
        onRetry={() => void records.refetch()}
        hasNext={records.hasNextPage}
        loadingMore={records.isFetchingNextPage}
        onLoadMore={() => void records.fetchNextPage()}
      />
    </>
  )
}

interface RecordFormProps {
  unit: 'kg' | 'lb'
  today: string
  /** 読み込んだ記録（同じ日付の記録があれば、今の値を入れて「上書きする」にする） */
  records: BodyRecord[]
}

/** 日付・体重・体脂肪率を記録する。同じ日付の記録があれば、今の値を入れてボタンを「上書きする」にする（F-90） */
function RecordForm({ unit, today, records }: RecordFormProps) {
  const toast = useToast()
  const queryClient = useQueryClient()
  const [recordedOn, setRecordedOn] = useState(today)
  const [weight, setWeight] = useState('')
  const [bodyFatPct, setBodyFatPct] = useState('')
  const [errors, setErrors] = useState<BodyErrors>({})
  const [saving, setSaving] = useState(false)
  const existing = records.find((record) => record.recordedOn === recordedOn) ?? null
  // その日の記録の値を入れたか（日付を変えたとき・一覧が届いたときに1回だけ入れる。描画中に状態を合わせる React の書き方）
  const [filledFor, setFilledFor] = useState<string | null>(null)
  if (existing !== null && filledFor !== recordedOn) {
    setFilledFor(recordedOn)
    setWeight(bodyWeightValue(existing.weightKg, unit))
    setBodyFatPct(existing.bodyFatPct === null ? '' : existing.bodyFatPct.toFixed(1))
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    const form = { recordedOn, weight, bodyFatPct }
    const found = validateBodyRecord(form, unit, today)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setSaving(true)
    try {
      await putBodyRecord(recordedOn, toBodyRecordInput(form, unit))
      toast(existing === null ? '体重を記録しました' : '体重を上書きしました')
      setFilledFor(recordedOn)
      void queryClient.invalidateQueries({ queryKey: BODY_KEY })
    } catch (err) {
      // 項目に結び付く誤りはその項目の下に、それ以外（通信の失敗など）はお知らせに出す。入力した値は消さない
      const known =
        err instanceof ApiError ? err.fieldErrors.filter((e) => FIELDS.has(e.field)) : []
      if (known.length > 0) {
        setErrors(Object.fromEntries(known.map((e) => [e.field, e.message])))
      } else {
        toast(errorMessage(err))
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <form className={styles.card} onSubmit={(e) => void submit(e)} noValidate>
      <div className={styles.fields}>
        <FormField
          label="日付"
          type="date"
          max={today}
          value={recordedOn}
          error={errors.recordedOn}
          onChange={(e) => {
            setRecordedOn(e.target.value)
            setFilledFor(null)
          }}
        />
        <FormField
          label={`体重（${unit}）`}
          inputMode="decimal"
          value={weight}
          error={errors.weight}
          onChange={(e) => setWeight(e.target.value)}
        />
        <FormField
          label="体脂肪率（%・任意）"
          inputMode="decimal"
          value={bodyFatPct}
          error={errors.bodyFatPct}
          onChange={(e) => setBodyFatPct(e.target.value)}
        />
      </div>
      <div className={styles.actions}>
        <button type="submit" className="button-primary" disabled={saving}>
          {existing === null ? '記録する' : '上書きする'}
        </button>
      </div>
    </form>
  )
}

/** 3か月・1年の体重と体脂肪率の推移（F-92） */
function TrendSection({ unit }: { unit: 'kg' | 'lb' }) {
  const headingId = useId()
  const [searchParams] = useSearchParams()
  const range: BodyRange = searchParams.get('range') === '1y' ? '1y' : '3m'
  const trend = useQuery({
    queryKey: [...BODY_KEY, 'trend', range],
    queryFn: () => fetchBodyTrend(range),
  })
  return (
    <section aria-labelledby={headingId} className={styles.card}>
      <h2 id={headingId} className={styles.cardTitle}>
        推移
      </h2>
      <div role="tablist" className={styles.tabs}>
        {RANGES.map((r) => (
          <Link
            key={r.range}
            to={r.range === '3m' ? '/body' : `/body?range=${r.range}`}
            replace
            preventScrollReset
            role="tab"
            aria-selected={r.range === range}
            className={styles.tab}
          >
            {r.label}
          </Link>
        ))}
      </div>
      <div role="tabpanel" aria-label={RANGES.find((r) => r.range === range)?.label}>
        {trend.error ? (
          <div className={styles.message}>
            <p>{errorMessage(trend.error)}</p>
            <button type="button" className="button-secondary" onClick={() => void trend.refetch()}>
              もう一度読み込む
            </button>
          </div>
        ) : !trend.data ? (
          <p className={styles.message}>読み込み中…</p>
        ) : (
          <BodyTrendChart points={bodyTrendPoints(trend.data, unit)} unit={unit} />
        )}
      </div>
    </section>
  )
}

interface RecordListProps {
  unit: 'kg' | 'lb'
  /** 読み込み中は null */
  records: BodyRecord[] | null
  error: Error | null
  onRetry: () => void
  hasNext: boolean
  loadingMore: boolean
  onLoadMore: () => void
}

/** 記録の一覧（日付の新しい順に30件ずつ）。確認ダイアログのあとに1件ずつ削除する（F-91） */
function RecordList({
  unit,
  records,
  error,
  onRetry,
  hasNext,
  loadingMore,
  onLoadMore,
}: RecordListProps) {
  const headingId = useId()
  const toast = useToast()
  const queryClient = useQueryClient()
  const [deleting, setDeleting] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function remove(date: string) {
    setBusy(true)
    try {
      await deleteBodyRecord(date)
      toast('体重の記録を削除しました')
      void queryClient.invalidateQueries({ queryKey: BODY_KEY })
    } catch (err) {
      toast(errorMessage(err))
    } finally {
      setBusy(false)
      setDeleting(null)
    }
  }

  return (
    <section aria-labelledby={headingId} className={styles.card}>
      <h2 id={headingId} className={styles.cardTitle}>
        記録の一覧
      </h2>
      {error ? (
        <div className={styles.message}>
          <p>{errorMessage(error)}</p>
          <button type="button" className="button-secondary" onClick={onRetry}>
            もう一度読み込む
          </button>
        </div>
      ) : records === null ? (
        <p className={styles.message}>読み込み中…</p>
      ) : records.length === 0 ? (
        <p className={styles.message}>まだ体重の記録がありません</p>
      ) : (
        <>
          <ul className={styles.rows}>
            {records.map((record) => {
              const date = record.recordedOn.replaceAll('-', '/')
              return (
                <li key={record.recordedOn} className={styles.row}>
                  <span className={styles.date}>{date}</span>
                  <span className={styles.weight}>{formatBodyWeight(record.weightKg, unit)}</span>
                  <span className={styles.fat}>
                    {record.bodyFatPct === null ? '—' : `${record.bodyFatPct.toFixed(1)}%`}
                  </span>
                  <button
                    type="button"
                    className={`button-secondary ${styles.delete}`}
                    aria-label={`${date} の記録を削除`}
                    onClick={() => setDeleting(record.recordedOn)}
                  >
                    削除
                  </button>
                </li>
              )
            })}
          </ul>
          {hasNext && <LoadMore loading={loadingMore} onLoad={onLoadMore} />}
        </>
      )}
      {deleting !== null && (
        <ConfirmDialog
          title={`${deleting.replaceAll('-', '/')} の体重の記録を削除しますか？`}
          confirmLabel="削除する"
          busy={busy}
          onConfirm={() => void remove(deleting)}
          onCancel={() => setDeleting(null)}
        />
      )}
    </section>
  )
}
