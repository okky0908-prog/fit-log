# FIT LOG（SNS風 筋トレ・運動記録アプリ）

筋トレ・運動の記録を投稿し、仲間と励まし合える SNS 風の Web アプリケーション。1回のトレーニング（種目・セットごとの重量と回数、有酸素運動の時間と距離、写真）を1件の投稿として記録し、仲間からの「ナイストレーニング」（いいね）やコメントを受け取れる。記録は週間・月間・年間で自動で集計され、自分で立てた目標の達成度を確認できる。

スクールの上級課題として開発する。前回の X 風 SNS アプリ（`SNSアプリ`）で身につけた「認証・投稿・画像・いいね・コメント・フォロー」を土台に、**種目 → セットの構造を持つ記録・SQL での集計・目標の達成率・公開範囲による閲覧の制御**など、一段複雑なデータと処理を扱うことが主な目的。詳細は[要件定義書](docs/requirements.md)を参照。

## アプリの方針

- **数字を競わせない**：ランキングや他人との記録の比較は置かない。比べる相手は「過去の自分」（自己ベストの更新、先週・先月との比較）
- **励まし合いに集中する**：いいねは「ナイストレーニング」。目標は仲間に公開して応援してもらえる
- **記録は非公開でも残せる**：人に見せたくない日の記録や体重は「自分のみ」にでき、非公開の記録も自分の集計・目標には含める

## 主な機能

- 新規登録・ログイン、メールアドレスの確認、パスワードの変更・リセット、退会
- トレーニング記録の投稿（種目とセット・画像4枚まで・公開範囲「公開／フォロワー限定／自分のみ」）。テンプレート・前回の記録の複製
- 自己ベスト更新の印、種目ごとの最大重量・推定 1RM の推移
- タイムライン（フォロー中・全体）、投稿の検索（ひとこと・ハッシュタグ・種目）
- ナイストレーニング、コメントと返信、フォロー、鍵アカウント（フォローの承認制）、通知
- 週間・月間の集計とカレンダー、ランニングの集計（距離・ペース・ベスト記録）、消費カロリーの目安
- 体重・体脂肪率の記録と推移（本人だけが見られる）
- 4種類の目標（頻度・量・重量・体重）と達成率、目標の公開と応援
- 単位の切り替え（kg／lb、km／マイル）

詳細は[機能要件](docs/features.md)・[画面仕様・画面遷移・ユースケース](docs/screens.md)を参照。

## 技術スタック

「前回と同じ」を前提にせず、このアプリの要件と性能を基準に比べて選んだ。比べた内容・選んだ理由・バージョンは[技術スタック](docs/tech-stack.md)を参照。

| 層 | 技術 |
|---|---|
| フロントエンド | React 19 + TypeScript 7 + Vite 8 + React Router、TanStack Query、React Hook Form + Zod、Recharts、date-fns |
| バックエンド | Java 25 + Spring Boot 4.1（Spring Security + JWT、仮想スレッド、保存の確定後に動くイベント） |
| データベース | PostgreSQL 17（pg_bigm）、jOOQ、Flyway |
| メール | Amazon SES（手元の開発・テストは Mailpit） |
| 画像 | Amazon S3（期限つきの署名付き URL で配信。手元の開発・テストは LocalStack） |
| インフラ | AWS（CloudFront + S3 + ALB + ECS Fargate + RDS + SES）を Terraform で構築 |
| テスト | JUnit 5・Mockito・Testcontainers・Awaitility・JaCoCo、Vitest・Testing Library、Playwright |

## ドキュメント

| ドキュメント | 内容 |
|---|---|
| [要件定義書](docs/requirements.md) | 全体の概要・目的・スコープ・方針・開発の進め方。各章から下の詳細ドキュメントへリンクする |
| [機能要件](docs/features.md) | 機能一覧（F-01〜F-113）と、機能ごとの決まりごと（入力の範囲・閲覧の制御・集計と目標の計算のしかた） |
| [画面仕様・画面遷移・ユースケース](docs/screens.md) | 画面一覧（23画面）・共通部品・各画面のワイヤーフレーム・画面遷移・ユースケース（36件） |
| [データ構造・ER図](docs/database.md) | ER図・テーブル定義・制約・インデックス・集計の SQL・閲覧の制御の条件・件数の上限の守り方 |
| [API 設計](docs/api.md) | REST API の一覧（76本）・共通のルール（単位・閲覧の制御・ページング）・エラーの一覧・リクエストとレスポンス |
| [技術スタック](docs/tech-stack.md) | 使う技術・バージョンと、比べた候補・選んだ理由 |
| [テスト計画書](docs/test-plan.md) | テストの技法・層ごとの対象、横断して確かめる観点（閲覧の制御・健康情報・単位の換算・日付など）、ケースの設計、完了の基準 |
| [モックアップ実装計画書](docs/mockup-plan.md) | 本実装の前に作った、HTML・CSS・JavaScript だけで動くモックアップの内容・初期データ・本実装との違い・確かめた結果 |

ER図・画面遷移図は Mermaid で書いている。VSCode では拡張機能「Markdown Preview Mermaid Support」を入れるとプレビューで表示できる。

## モックアップ

本実装の前に、画面と動きを目で確かめるためのモックアップを `mockup/` に置いている（外部のライブラリを使わない HTML・CSS・JavaScript。データはブラウザのメモリだけで、再読み込みで初期状態に戻る）。

- `mockup/index.html` をブラウザで直接開く（または `cd mockup && python3 -m http.server 8000` で `http://localhost:8000`）
- ログイン画面の「デモ用アカウント」を押すと入力される（パスワードはすべて `password123`）
- 詳細は[モックアップ実装計画書](docs/mockup-plan.md)を参照

## 開発の状況

本実装の段階 1 を進めている（PR の順番は[テスト計画書 8.2](docs/test-plan.md#82-段階-1-の-pr-の順番)）。実装は次の段階で進める（[要件定義書 11章](docs/requirements.md#11-開発の進め方)・[テスト計画書 8章](docs/test-plan.md#8-進め方)）。

| 段階 | 内容 | 状況 | 目安 | 実績 |
|---|---|---|---|---|
| — | 要件定義・設計のドキュメント | 済み | — | — |
| — | モックアップで画面を確かめる | 済み | — | — |
| 0 | テストの土台（CI・Testcontainers・カバレッジの基準） | 済み | 4日 | 3日 |
| 1 | 認証（確認メールを含む）・記録・ナイストレーニング・コメントと返信・フォロー・タイムライン、それらが作る通知 | 進行中（PR 29本中28本） | 16日 | |
| 2 | 記録と集計（自己ベスト・週間と月間・カレンダー・ランニング・種目ごとの推移・体重） | これから | 10日 | |
| 3 | 目標 | これから | 7日 | |
| 4 | 鍵アカウント・検索・テンプレート | これから | 5日 | |
| 5 | アカウント管理・単位・消費カロリー | これから | 4日 | |
| 6 | アクセシビリティ・スマートフォン幅・セキュリティなど、シナリオでは足りない観点 | これから | 4日 | |

**段階 1 の進み具合**（2026-11-25 時点。9 を 9-1〜9-5、10 を 10-1・10-2、11 を 11-1〜11-4、12 を 12-1・12-2、13 を 13-1〜13-4、14 を 14-1・14-2 に分けたので29本。PR の順番は[テスト計画書 8.2](docs/test-plan.md#82-段階-1-の-pr-の順番)）

- 済み（28本）：起動の手順、新規登録・ログイン、再発行・ログアウト、確認メール、画面の土台と認証の画面、種目、記録の作成・詳細、記録の編集・削除、記録の画像（作成と詳細・編集と削除）、投稿詳細の画面（9-1）、記録の作成・編集の画面の中心（9-2）、記録の画像の画面（9-3）、記録の作成の画面の残り（9-4）、自分専用の種目の画面（9-5）、通知のバックエンド（10-1）、通知の画面とナビゲーション（10-2）、フォローとプロフィールのバックエンド（11-1）、プロフィールの編集とアイコン（11-2）、プロフィールとフォロー一覧の画面（11-3）、プロフィール編集の画面（11-4）、タイムラインと記録一覧のバックエンド（12-1）、タイムラインの画面と投稿カード（12-2）、ナイストレーニングのバックエンド（13-1）、コメント・返信のバックエンド（13-2）、ナイストレーニングのボタン（13-3）、投稿詳細のコメント欄（13-4）、E2E の土台（14-1）
- 次：段階 1 のユースケースの E2E と、段階の終わりの確認（14-2）
- ここまでの作業日は7日（2026-11-19〜25。19日は段階 0 の最後の作業と重なる）。残りは、ナイストレーニング・コメントの API と、段階の終わりの E2E

**目安と実績**（単位は作業日。コミットのある日を1日と数える）

- 目安は、前回の SNS アプリの実績（API 27本・画面 10 を、実装とテストで約11日。1日あたり API 約2.5本）をもとに、段階ごとの API の数から出した。SQL の集計・目標の判定が中心の段階 2・3 は2倍にしている。段階 1 は、確認メール（API 2本と画面1つ）を段階 5 から移したので、1日足している
- 実績は、段階を終える PR で git から数えて入れる（例：`git log --since=<始めた日> --until=<終えた日> --format=%ad --date=short | sort -u | wc -l`）
- 段階 1 を終えたら、その実績の速さで段階 2 以降の目安を計算し直す（前回の実績は1回分しかないため）
- 目安の 1.5 倍を超えたら一度止まり、範囲を削るか目安を直すかを決める。段階の途中で思いついた改善は Issue に書いて後に回す

## セットアップとテスト

必要なもの：Java 25（Homebrew の `openjdk@25`）、Docker（Docker Desktop）、Node.js 24（`frontend/.nvmrc`）。

### バックエンド

```bash
cd backend
./gradlew build    # 整形の確認・コンパイル・テスト・カバレッジの基準の確認
./gradlew test     # テストだけ
./gradlew spotlessApply   # 整形する
```

- カバレッジのレポートは `backend/build/reports/jacoco/test/html/index.html`。基準（計算の層 100%・Service の層 90%。[テスト計画書 6章](docs/test-plan.md#6-完了の基準)）を下回ると `build` が落ちる。アプリのクラスは `<機能>/<層>/` の下に置く（層の外に置くとテストが落ちる）

- テストは、使い捨ての PostgreSQL（pg_bigm 入りの自作イメージ）のコンテナで動く。開発用の DB は使わないので、前回の SNS アプリのコンテナが動いていてもテストは動く（ポートが当たらない）
- 初回は、PostgreSQL のイメージのビルド（pg_bigm をソースからビルドする）に数分かかる。2回目からは Docker のキャッシュで速く終わる
- jOOQ のコード（テーブルの形の Java のクラス）は、ビルドの中で `generateJooq` タスクが作る（`backend/build/generated-sources/jooq`）。マイグレーションを変えたときだけ、作り直す

### フロントエンド

```bash
cd frontend
npm ci
npm run dev             # 開発サーバー（http://localhost:5173。/api はバックエンドの 8080 に中継する）
npm test                # テスト（Vitest）
npm run test:coverage   # テストとカバレッジの基準の確認
npm run lint            # Lint（oxlint。警告も失敗にする）
npm run format          # 整形する（Prettier。npm run format:check は確認だけ）
npm run build           # 型チェックとビルド
```

- カバレッジのレポートは `frontend/coverage/index.html`。基準（`src/lib` の分岐網羅をファイルごとに 90%）を下回ると `test:coverage` が落ちる。ファイルは `src/lib`・`src/components`・`src/pages` のどれかに置く（外に置くとテストが落ちる）
- CI は、このうち4つ（整形の確認・Lint・型チェックとビルド・テストとカバレッジ）とバックエンドの `build` を行う。どちらも通らないとマージできない

### E2E テスト（Playwright）

```bash
cd frontend
npx playwright install chromium   # 初回だけ：ブラウザを入れる
npm run test:e2e                  # バックエンド（bootTestRun）とフロントの開発サーバーを起動して、本物のブラウザで通す
```

- バックエンドは `./gradlew bootTestRun` で、テストと同じ使い捨てのコンテナ（PostgreSQL・Mailpit・LocalStack）につないで起動する（`backend/src/test/java/com/okimoto/fitlog/TestFitLogApplication.java`）。開発用の DB・メール・S3 には書き込まない
- ポートは開発と同じ 8080・5173。開発用のバックエンドが 8080 で動いていたら、止めてから実行する（使い回すと開発用の DB に書き込むため、使い回さない）
- テストは `frontend/e2e/`。どの画面でも、アクセシビリティの違反（axe）がないことを確かめる
- CI でも E2E のジョブで実行する。バックエンド・フロントエンドと同じく、通らないとマージできない

## アプリの起動

前回の SNS アプリとポートが同じなので、先に SNS アプリのコンテナとサーバーを止める（`cd ../SNSアプリ && docker compose stop`）。

```bash
cp .env.example .env                    # 初回だけ。JWT_SECRET に openssl rand -base64 48 の結果を入れる
docker compose up -d --wait postgres mailpit localstack   # DB（pg_bigm 入り。初回はイメージのビルドで数分かかる）・メールの確認・S3 の代わり

# バックエンド（http://localhost:8080。起動のときに Flyway でテーブルを作る）
cd backend && JAVA_HOME=$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home ./gradlew bootRun

# フロントエンド（別のターミナルで。http://localhost:5173）
cd frontend && npm ci && npm run dev
```

- 送ったメール（新規登録の確認メールなど）は、Mailpit の画面 http://localhost:8025 で見る
- ポートは固定（PostgreSQL 5432・Mailpit 1025／8025・LocalStack 4566・バックエンド 8080・フロントエンド 5173）。使われていたら、別のポートに逃がさず、使っているプロセスを止める（[CLAUDE.md](CLAUDE.md)）
- 詳しい手順と、ポートが競合したときの対応は `.claude/skills/run/SKILL.md`
- DB を作り直すとき：`docker compose down -v`（データがすべて消える）

## ディレクトリ構成

```text
backend/                     Spring Boot（Java 25・Gradle）
  src/main/java/com/okimoto/fitlog/
    <機能>/controller/       API の入口（例：exercise/controller）
    <機能>/service/          処理の流れ（分岐網羅 90% 以上）
    <機能>/calc/             計算（分岐網羅 100%）
    <機能>/repository/       DB の読み書き（jOOQ）
    <機能>/config/           Spring の設定・フィルター（例：auth/config の Spring Security）
    common/                  機能をまたいで使う部品（エラーの形・入力チェック・時計）
  src/main/resources/db/migration/   Flyway のマイグレーション
  src/codegen/               jOOQ のコードを作るプログラム（アプリの jar には入らない）
  src/test/                  テスト（support/TestcontainersConfiguration：テスト用のコンテナ）
frontend/                    React・TypeScript・Vite
  src/lib/                   関数・通信・ログインの状態（分岐網羅 90% 以上）。api/client.ts が API の呼び出しとトークンの再発行
  src/components/            画面の部品（隣に 〇〇.test.tsx）
  src/pages/                 画面。routes.tsx が画面の URL の一覧
  src/test/                  テストの共通の組み立て（偽物の API・アプリの描画）
  src/test/                  テストの共通の準備（setup.ts）・層のフォルダーのテスト
db/Dockerfile                pg_bigm 入りの PostgreSQL 17（テストと docker-compose で共通）
docker-compose.yml           手元の開発の DB・メール（Mailpit）・S3 の代わり（LocalStack）
localstack/init/ready.d/     LocalStack の起動時に画像のバケットを作るスクリプト
docs/                        設計のドキュメント
mockup/                      モックアップ（本実装では直さない参考資料）
```

## 開発の進め方

リポジトリの運用ルールは [CLAUDE.md](CLAUDE.md) にまとめている。

- `main` へ直接コミット・push しない。作業ごとに GitHub の Issue を作り、Issue に紐づくブランチ（`<type>/<Issue番号>-<短い英語の説明>`。例：`docs/21-readme`）で作業して、`main` 宛の PR を作る
- 機能の追加・動きの変更・不具合の修正はテスト駆動（TDD）で進める。先に失敗するテストを書き（Red）、通す最小限のコードを書き（Green）、整理する（Refactor）。テストのケースは[テスト計画書](docs/test-plan.md)の技法で決める
- 動作確認のポートは固定する（バックエンド 8080・フロントエンド 5173・PostgreSQL 5432・LocalStack 4566・Mailpit 1025／8025）。前回の SNS アプリとポートが同じなので、同時に起動しない
