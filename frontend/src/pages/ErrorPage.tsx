import styles from './AuthPage.module.css'

/** 表示中に想定外のエラーが起きたとき（真っ白な画面の代わり） */
export function ErrorPage() {
  return (
    <main className={styles.page}>
      <div className={styles.card}>
        <h1 className={styles.title}>エラーが発生しました</h1>
        <p className={styles.message}>お手数ですが、ページを読み込み直してください。</p>
        <div className={styles.links}>
          <a href="/">ホームへ</a>
        </div>
      </div>
    </main>
  )
}
