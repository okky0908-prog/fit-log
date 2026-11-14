# API 設計

[要件定義書](./requirements.md) の詳細ドキュメント。React（フロントエンド）から Spring Boot（バックエンド）を呼び出す REST API（JSON）をまとめる。機能 ID（F-xx）は [機能要件](./features.md)、画面 ID（S-xx）は [画面仕様](./screens.md)、テーブルは [データ構造](./database.md) を参照。

## この文書と API 仕様書（OpenAPI）の分担

前回の SNS アプリと同じく、実装したあとは、各 API のリクエスト・レスポンスの形（項目・型・必須か・null になりうるか）を、コードから自動で作る API 仕様書（`docs/openapi.yaml`）に任せる。同じことを2か所に手で書くと、片方だけ直し忘れてずれるため。

| 段階 | この文書（api.md） | openapi.yaml |
|---|---|---|
| 設計（今） | 共通のルール・エラー・API 一覧・**主なデータの形の案**・各 API の決まり | まだない |
| 実装したあと | 共通のルール・エラー・API 一覧・各 API の決まり。データの形の節は、openapi.yaml への案内に置き換える | 各 API の形（springdoc-openapi がコントローラー・record から作る） |

- 仕様書の作り方・CI での確かめ方・フロントの型の自動生成（openapi-typescript）は、前回の SNS アプリと同じ（`OpenApiSpecTest` が openapi.yaml を書き出し、CI が差分を確かめる）
- 実装するときにこの文書の形と違う形にした場合は、理由をこの文書の決まりの節に残す

## 共通のルール

| 項目 | 内容 |
|---|---|
| ベース URL | `/api` |
| データ形式 | JSON。画像を送る API（A-20・A-22・A-82）だけ `multipart/form-data` |
| 認証 | アクセストークン（JWT、15分）を `Authorization: Bearer <token>` で送る。リフレッシュトークン（14日）は HttpOnly Cookie で届き、ブラウザが `/api/auth` の下にだけ自動で送る（前回と同じ） |
| JSON のキー | キャメルケース（例：`likeCount`） |
| 日付 | `YYYY-MM-DD`（例：`2026-11-14`）。実施日・体重の日付・期限・集計の期間 |
| 日時 | ISO 8601 の日本時間（例：`2026-11-14T10:15:00+09:00`）。投稿日時・通知の日時など |
| 「今日」 | サーバーが日本時間で決める（未来の日付のチェック・今週・今月） |
| ページング | カーソル方式。最初は `cursor` を付けずに呼び、続きはレスポンスの `nextCursor` をそのまま `?cursor=` に渡す（[ページング](#ページング)） |
| ID | 数値（`id`）。ユーザーだけは URL で `username` を使う（前回と同じ） |

### 単位

[要件定義書 5.14](./requirements.md) の「保存・集計は kg・km、入力と表示はユーザーが選んだ単位」を、API では次のように分ける。

| 向き | 単位 | 理由 |
|---|---|---|
| **リクエスト**（記録・テンプレート・目標・体重の入力） | ユーザーが入力した単位のまま送り、どの単位かを `weightUnit`（`kg`／`lb`）・`distanceUnit`（`km`／`mi`）で一緒に送る | 刻み（kg は 0.25、lb は 0.5 など）のチェックは入力した単位でしかできないため。kg・km への換算と丸めは、サーバーの1か所（`BigDecimal`）で行う（[データ構造](./database.md#単位の換算と小数の桁)） |
| **レスポンス** | 常に kg・km・秒（キーに `Kg`・`Km`・`Sec` を付ける。例：`weightKg`） | 見る人ごとに単位が違うので、表示の換算は画面で行う（単位の設定はログイン中のユーザーの情報 A-03 にある） |

- 単位の設定（A-13）とは別にリクエストごとに単位を送るのは、入力の途中で設定を変えても、入力した値が別の単位として保存されないようにするため
- ペースは「1km あたりの秒数」（`paceSecPerKm`）で返す。マイル表示は画面で `× 1.609344` する
- 時間の合計・運動時間は秒（`durationSec`）。トレーニング時間だけは入力が分なので `durationMin`

### 閲覧の制御

[機能要件の閲覧の制御](./features.md#閲覧の制御)を、すべての API で同じ条件（[データ構造の閲覧の制御の条件](./database.md#閲覧の制御の条件)）で判定する。

- **見てはいけない投稿は、存在しない投稿と同じ 404（`WORKOUT_NOT_FOUND`）を返す**。詳細・コメント一覧・コメントの投稿・ナイストレーニング・消費カロリー・テンプレートとして保存の元データ、のどれでも同じ。403 にすると「その ID の投稿はある」と分かってしまうため
- 一覧（タイムライン・プロフィール・検索・カレンダー・通知）は、見てよいものだけを返す（見てはいけないものは、はじめからなかったことにする）
- **見られるが、本人でないと操作できない**ものは 403（`FORBIDDEN`）。例：他人の投稿の編集・削除、他人のコメントの削除（投稿者は除く）
- 目標（公開した目標の一覧・応援）も同じ考え方で、見てはいけない目標は 404（`GOAL_NOT_FOUND`）
- 体重・体脂肪率・消費カロリー・体重の目標・集計値は、**本人の API の応答にしか入れない**（他人の投稿の応答に null で入れることもしない。消費カロリーは別の API A-24 にする）

### 画像の URL

S3 のバケットは公開しないので、画像（記録の画像・アイコン）の URL は、閲覧を許可したあとに API が**署名付き URL**（期限つき）を作って返す（[技術スタック 3.9](./tech-stack.md)）。

- 署名に使う時刻を「今の1時間の始まり」にそろえ、有効期限を2時間にする。同じ1時間の間は同じ URL を返すので、ブラウザのキャッシュが効く。受け取った URL は、少なくとも1時間は使える
- 画面は URL を保存しない（TanStack Query のキャッシュの間だけ使う）。期限が切れた画像は、一覧を取り直すと新しい URL になる
- アイコンも同じく署名付き URL。アイコンは、ログインしている全員に見せてよい情報（鍵アカウントでも）なので、閲覧の判定はしない

### ページング

一覧の API は、`items`（中身の配列）・`nextCursor`（続きがなければ `null`）・`hasNext` の形で返す。1回の件数は API ごとに決める（ほとんど20件）。

```json
{ "items": [ ... ], "nextCursor": "eyJjIjoiMjAyNi0xMS0xNFQxMDoxNTowMCswOTowMCIsImkiOjEyM30", "hasNext": true }
```

- `nextCursor` の中身は画面で解釈しない（サーバーが「どこまで返したか」を入れた文字列）。形式が違えば 400（`VALIDATION_FAILED`、`field` は `cursor`）
- カーソルに入れる値は、一覧の並び順の列（例：タイムラインなら投稿日時と ID、プロフィールなら実施日・投稿日時・ID）

## エラー

エラーはすべて次の形で返す（前回と同じ）。

```json
{
  "status": 400,
  "code": "VALIDATION_FAILED",
  "message": "入力内容に誤りがあります",
  "errors": [
    { "field": "exercises[0].sets[2].weight", "message": "0.25kg 刻みで入力してください" }
  ]
}
```

| 項目 | 内容 |
|---|---|
| `status` | HTTP ステータスと同じ値 |
| `code` | エラーの種類。**画面の分岐はステータスや文言ではなく `code` で行う** |
| `message` | 利用者にそのまま見せてよい文言。内部の情報は入れない |
| `errors` | 項目ごとの入力エラー。入れ子の項目は `exercises[0].sets[2].weight` の形で場所を示す（画面がその欄の下に赤字を出せるように）。なければ空の配列 |

| ステータス | `code` | 使う場面 | 前回 |
|---|---|---|---|
| 400 | `VALIDATION_FAILED` | 入力のチェックのエラー（`errors` に項目ごとの内容） | ○ |
| 400 | `MALFORMED_REQUEST` | JSON が壊れているなど、リクエストを読めない | ○ |
| 400 | `CANNOT_FOLLOW_SELF` | 自分自身をフォローしようとした | ○ |
| 400 | `CANNOT_CHEER_OWN_GOAL` | 自分の目標を応援しようとした | 新規 |
| 400 | `INCORRECT_PASSWORD` | メールアドレスの変更・パスワードの変更・退会で、今のパスワードが違う。401 にしないのは、画面が 401 を「トークンの再発行」として扱うため | 新規 |
| 400 | `INVALID_TOKEN` | 確認用・再設定用のリンクのトークンが、期限切れ・使用済み・不正 | 新規 |
| 400 | `BODY_RECORD_REQUIRED` | 体重の記録が1件もないのに、体重の目標を作ろうとした | 新規 |
| 401 | `UNAUTHENTICATED` | アクセストークンがない・無効・期限切れ。画面は A-04 で再発行して1回だけやり直す | ○ |
| 401 | `INVALID_CREDENTIALS` | ログインで、メールアドレスかパスワードが違う | ○ |
| 401 | `SESSION_EXPIRED` | リフレッシュトークンがない・無効・期限切れ | ○ |
| 403 | `FORBIDDEN` | 見られるが、操作する権限がない（他人の投稿の編集など） | ○ |
| 403 | `PRIVATE_ACCOUNT` | 鍵アカウントのフォロー・フォロワー一覧を、承認済みのフォロワーでない人が見ようとした | 新規 |
| 404 | `WORKOUT_NOT_FOUND` | 記録が存在しない・見てはいけない（前回の `POST_NOT_FOUND` を置き換え） | 変更 |
| 404 | `COMMENT_NOT_FOUND` | コメントが存在しない・その投稿が見てはいけない | ○ |
| 404 | `USER_NOT_FOUND` | ユーザーが存在しない | ○ |
| 404 | `EXERCISE_NOT_FOUND` | 種目が存在しない・他人の自分専用の種目 | 新規 |
| 404 | `TEMPLATE_NOT_FOUND` | テンプレートが存在しない・他人のテンプレート | 新規 |
| 404 | `GOAL_NOT_FOUND` | 目標が存在しない・見てはいけない | 新規 |
| 404 | `BODY_RECORD_NOT_FOUND` | その日の体重の記録がない | 新規 |
| 404 | `FOLLOW_REQUEST_NOT_FOUND` | 承認・拒否しようとしたリクエストがない（取り消された） | 新規 |
| 404 | `RESOURCE_NOT_FOUND` | 存在しない URL | ○ |
| 405 | `METHOD_NOT_ALLOWED` | その URL で使えないメソッド | ○ |
| 409 | `ALREADY_REGISTERED` | ユーザー名・メールアドレスが登録済み（どの項目かは `errors`） | ○ |
| 409 | `EXERCISE_NAME_TAKEN` | 自分専用の種目の名前が、最初から用意された種目か自分の種目と同じ | 新規 |
| 409 | `EXERCISE_IN_USE` | 記録・目標で使っている自分専用の種目を削除しようとした | 新規 |
| 409 | `EXERCISE_LIMIT_EXCEEDED` | 自分専用の種目が100件ある | 新規 |
| 409 | `TEMPLATE_LIMIT_EXCEEDED` | テンプレートが20件ある | 新規 |
| 409 | `GOAL_LIMIT_EXCEEDED` | 達成済み以外の目標が10件ある | 新規 |
| 409 | `ALREADY_VERIFIED` | 確認済みのメールアドレスに、確認メールを再送しようとした | 新規 |
| 413 | `PAYLOAD_TOO_LARGE` | リクエスト全体の大きさが上限（10MB）を超えた | ○ |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Content-Type が違う | ○ |
| 429 | `TOO_MANY_LOGIN_ATTEMPTS` | 同じメールアドレスでログインの失敗が続いた | ○ |
| 429 | `TOO_MANY_EMAILS` | 同じアドレスへのメールが1時間で5通を超える（確認メールの再送・メールアドレスの変更）。パスワードのリセット（A-08）では使わない | 新規 |
| 500 | `INTERNAL_ERROR` | 想定していないエラー | ○ |
| 500 | `IMAGE_UPLOAD_FAILED` | 画像を S3 に保存できなかった（記録・プロフィールも保存しない） | ○ |
| 503 | `SERVICE_UNAVAILABLE` | DB につながらないなど | ○ |
| 503 | `SERVER_BUSY` | ログイン・新規登録が集中した | ○ |

- 種類はバックエンドの `ErrorCode`（列挙型）で一元管理する。一度使い始めた名前は変えない
- 1枚の画像が大きすぎる・形式が違うときは、413 ではなく 400（`VALIDATION_FAILED`、`field` は `images[1]` など）にする。どの画像が悪いかを画面に出せるように

## API 一覧

「認証」の列：必要 ＝ アクセストークンが必要。不要 ＝ ログインしていなくても呼べる。

### 認証・アカウント

| ID | メソッド | パス | 認証 | 概要 | 機能 |
|---|---|---|---|---|---|
| A-01 | POST | `/api/auth/signup` | 不要 | 新規登録。トークンを返し、確認メールを送る | F-01, F-05 |
| A-02 | POST | `/api/auth/login` | 不要 | ログイン（メールアドレスとパスワード） | F-02 |
| A-03 | GET | `/api/auth/me` | 必要 | ログイン中のユーザーの情報（単位・鍵アカウント・メールアドレスの確認を含む） | F-04 |
| A-04 | POST | `/api/auth/refresh` | Cookie | アクセストークンの再発行 | F-04 |
| A-05 | POST | `/api/auth/logout` | Cookie | ログアウト | F-03 |
| A-06 | POST | `/api/auth/email-verification` | 不要 | メールアドレスの確認（リンクのトークンを送る） | F-05, F-06 |
| A-07 | POST | `/api/account/email-verification` | 必要 | 確認メールの再送 | F-05 |
| A-08 | POST | `/api/auth/password-reset` | 不要 | パスワードのリセットの申請（再設定のリンクを送る） | F-08 |
| A-09 | POST | `/api/auth/password-reset/confirm` | 不要 | パスワードの再設定 | F-08 |
| A-10 | POST | `/api/account/email` | 必要 | メールアドレスの変更の申請（新しいアドレスに確認のリンクを送る） | F-06 |
| A-11 | PUT | `/api/account/password` | 必要 | パスワードの変更 | F-07 |
| A-12 | DELETE | `/api/account` | 必要 | 退会 | F-09 |
| A-13 | PATCH | `/api/account/settings` | 必要 | 設定の変更（単位・鍵アカウント） | F-63, F-53 |

### 記録・種目・テンプレート

| ID | メソッド | パス | 認証 | 概要 | 機能 |
|---|---|---|---|---|---|
| A-20 | POST | `/api/workouts` | 必要 | 記録の作成（multipart。記録の JSON と画像） | F-10 |
| A-21 | GET | `/api/workouts/{workoutId}` | 必要 | 記録の詳細 | F-13, F-85 |
| A-22 | PUT | `/api/workouts/{workoutId}` | 必要（本人） | 記録の編集（multipart） | F-11 |
| A-23 | DELETE | `/api/workouts/{workoutId}` | 必要（本人） | 記録の削除 | F-12 |
| A-24 | GET | `/api/workouts/{workoutId}/calories` | 必要（本人） | 記録の消費カロリーの目安 | F-86 |
| A-30 | GET | `/api/exercises?q=&bodyPart=` | 必要 | 種目の検索（最初から用意された種目と自分専用の種目） | F-14 |
| A-31 | GET | `/api/exercises/recent` | 必要 | 自分が最近使った種目（10件） | F-14 |
| A-32 | POST | `/api/exercises` | 必要 | 自分専用の種目の追加 | F-15 |
| A-33 | PUT | `/api/exercises/{exerciseId}` | 必要（本人） | 自分専用の種目の名前・部位の変更 | F-16 |
| A-34 | DELETE | `/api/exercises/{exerciseId}` | 必要（本人） | 自分専用の種目の削除 | F-16 |
| A-40 | GET | `/api/templates` | 必要 | テンプレートの一覧 | F-18, F-19 |
| A-41 | GET | `/api/templates/{templateId}` | 必要（本人） | テンプレート1件（種目とセット） | F-18, F-19 |
| A-42 | POST | `/api/templates` | 必要 | テンプレートの保存 | F-17 |
| A-43 | PUT | `/api/templates/{templateId}` | 必要（本人） | テンプレートの変更（名前・種目・セット） | F-18 |
| A-44 | DELETE | `/api/templates/{templateId}` | 必要（本人） | テンプレートの削除 | F-18 |

### タイムライン・検索・コメント・ナイストレーニング

| ID | メソッド | パス | 認証 | 概要 | 機能 |
|---|---|---|---|---|---|
| A-50 | GET | `/api/timeline?cursor=` | 必要 | フォロー中タイムライン | F-20, F-21 |
| A-51 | GET | `/api/timeline/all?cursor=` | 必要 | 全体タイムライン | F-22, F-21 |
| A-52 | GET | `/api/timeline/new-count?since=` | 必要 | フォロー中タイムラインの新しい投稿の件数 | F-24 |
| A-53 | GET | `/api/timeline/all/new-count?since=` | 必要 | 全体タイムラインの新しい投稿の件数 | F-24 |
| A-54 | GET | `/api/workouts/search?q=&tag=&exerciseId=&cursor=` | 必要 | 投稿の検索（ひとこと・ハッシュタグ・種目） | F-25〜F-27 |
| A-55 | GET | `/api/hashtags/mine` | 必要 | 自分がよく使うハッシュタグ（10個まで） | S-11 |
| A-60 | GET | `/api/workouts/{workoutId}/comments?cursor=` | 必要 | コメント一覧（返信を含む） | F-31 |
| A-61 | POST | `/api/workouts/{workoutId}/comments` | 必要 | コメント・返信の投稿 | F-30, F-33 |
| A-62 | DELETE | `/api/comments/{commentId}` | 必要（書いた人・投稿者） | コメントの削除 | F-32 |
| A-63 | POST | `/api/workouts/{workoutId}/likes` | 必要 | ナイストレーニング | F-40 |
| A-64 | DELETE | `/api/workouts/{workoutId}/likes` | 必要 | ナイストレーニングの取り消し | F-41 |

### フォロー・プロフィール・ユーザー検索

| ID | メソッド | パス | 認証 | 概要 | 機能 |
|---|---|---|---|---|---|
| A-70 | POST | `/api/users/{username}/follow` | 必要 | フォロー（鍵アカウントならリクエスト） | F-50 |
| A-71 | DELETE | `/api/users/{username}/follow` | 必要 | フォロー解除・リクエストの取り消し | F-51, F-57 |
| A-72 | GET | `/api/users/{username}/following?cursor=` | 必要 | フォロー中一覧 | F-52 |
| A-73 | GET | `/api/users/{username}/followers?cursor=` | 必要 | フォロワー一覧 | F-52 |
| A-74 | GET | `/api/follow-requests?cursor=` | 必要 | 自分へのフォローのリクエスト一覧 | F-54 |
| A-75 | POST | `/api/follow-requests/{username}/approve` | 必要 | リクエストの承認 | F-55 |
| A-76 | DELETE | `/api/follow-requests/{username}` | 必要 | リクエストの拒否 | F-56 |
| A-80 | GET | `/api/users/{username}` | 必要 | プロフィール | F-60 |
| A-81 | GET | `/api/users/{username}/workouts?cursor=` | 必要 | そのユーザーの記録一覧（実施日の新しい順） | F-62, F-19 |
| A-82 | PUT | `/api/users/me` | 必要 | プロフィールの編集（multipart） | F-61 |
| A-83 | GET | `/api/users/search?q=&cursor=` | 必要 | ユーザー検索。`q` が空なら新しいユーザー | F-70, F-71 |
| A-84 | GET | `/api/users/{username}/goals` | 必要 | そのユーザーの公開した目標 | F-106 |

### 集計

すべて本人の記録だけを集計する。

| ID | メソッド | パス | 認証 | 概要 | 機能 |
|---|---|---|---|---|---|
| A-90 | GET | `/api/stats/weekly?date=` | 必要 | 週間の集計 | F-80, F-86 |
| A-91 | GET | `/api/stats/monthly?month=` | 必要 | 月間の集計 | F-81, F-86 |
| A-92 | GET | `/api/stats/calendar?month=` | 必要 | カレンダー（記録した日の一覧） | F-82 |
| A-93 | GET | `/api/stats/calendar/workouts?date=` | 必要 | その日の自分の記録 | F-82 |
| A-94 | GET | `/api/stats/running?period=&date=` | 必要 | ランニングの集計（週間・月間・年間） | F-83 |
| A-95 | GET | `/api/stats/exercises` | 必要 | 自分が記録した種目の一覧 | F-84 |
| A-96 | GET | `/api/stats/exercises/{exerciseId}?range=` | 必要 | 種目ごとの推移と自己ベスト | F-84, F-85 |
| A-97 | GET | `/api/stats/this-week` | 必要 | ホームの「今週のひとこと」（今週の日数と、毎週の頻度の目標） | S-10 |

### 体重・目標

| ID | メソッド | パス | 認証 | 概要 | 機能 |
|---|---|---|---|---|---|
| A-100 | PUT | `/api/body-records/{date}` | 必要 | 体重の記録（その日の記録を作る・上書きする） | F-90 |
| A-101 | GET | `/api/body-records?cursor=` | 必要 | 体重の一覧（日付の新しい順に30件ずつ） | F-91 |
| A-102 | DELETE | `/api/body-records/{date}` | 必要 | 体重の記録の削除 | F-91 |
| A-103 | GET | `/api/body-records/trend?range=` | 必要 | 体重・体脂肪率の推移 | F-92 |
| A-110 | GET | `/api/goals?status=` | 必要 | 自分の目標の一覧（進行中・達成済み） | F-103, F-105 |
| A-111 | POST | `/api/goals` | 必要 | 目標の作成 | F-100, F-106 |
| A-112 | GET | `/api/goals/{goalId}` | 必要（本人） | 目標1件（編集の画面用） | F-101 |
| A-113 | PUT | `/api/goals/{goalId}` | 必要（本人） | 目標の編集 | F-101, F-106 |
| A-114 | DELETE | `/api/goals/{goalId}` | 必要（本人） | 目標の削除 | F-102 |
| A-115 | GET | `/api/goals/{goalId}/achievements` | 必要（本人） | くり返しの目標の、週・月ごとの達成の履歴 | F-105 |
| A-116 | POST | `/api/goals/{goalId}/cheers` | 必要 | 応援する | F-107 |
| A-117 | DELETE | `/api/goals/{goalId}/cheers` | 必要 | 応援を取り消す | F-107 |

### 通知・そのほか

| ID | メソッド | パス | 認証 | 概要 | 機能 |
|---|---|---|---|---|---|
| A-120 | GET | `/api/notifications?cursor=` | 必要 | 通知一覧 | F-111 |
| A-121 | GET | `/api/notifications/unread-count` | 必要 | 未読の数と、フォローのリクエストの数 | F-112 |
| A-122 | POST | `/api/notifications/read` | 必要 | 通知を既読にする | F-113 |
| A-130 | POST | `/api/client-errors` | 不要 | 画面で起きたエラーの報告（前回と同じ） | 運用 |

- ALB のヘルスチェック用に `GET /api/health`（認証不要、200 を返すだけ）を用意する（前回と同じ）
- `/api/users/search` と `/api/users/me` が `/api/users/{username}` と同じ形なので、`search` というユーザー名は新規登録で使えない（前回と同じ。`me` は4文字未満なので、もともと使えない）

## 主なデータの形（設計時点の案）

実装したあとは openapi.yaml に任せ、この節は短くする。

### UserSummary（投稿者・コメントした人など）

```json
{ "username": "yamada", "displayName": "山田太郎", "iconUrl": "https://…", "isPrivate": false }
```

- `iconUrl` は、アイコンがなければ `null`（画面は表示名の頭文字を出す）
- `isPrivate` は鍵のアイコンを出すために使う

### Me（A-01〜A-04・A-13 の `user`）

```json
{
  "username": "yamada", "displayName": "山田太郎", "iconUrl": null, "bio": "週4で筋トレしています",
  "email": "yamada@example.com", "emailVerified": true, "isPrivate": false,
  "weightUnit": "kg", "distanceUnit": "km"
}
```

### Workout（記録。A-20〜A-22・タイムライン・検索・プロフィール・カレンダー）

```json
{
  "id": 123,
  "author": { "username": "yamada", "displayName": "山田太郎", "iconUrl": null, "isPrivate": false },
  "performedOn": "2026-11-14",
  "createdAt": "2026-11-14T19:30:00+09:00",
  "editedAt": null,
  "body": "今日は脚の日！ #脚トレ",
  "durationMin": 75,
  "visibility": "public",
  "exercises": [
    {
      "exerciseId": 16, "name": "スクワット", "bodyPart": "legs", "recordType": "weight_reps", "isRunning": false,
      "personalBest": true,
      "sets": [
        { "weightKg": 60.000, "reps": 10, "durationSec": null, "distanceKm": null, "personalBest": false },
        { "weightKg": 100.000, "reps": 5, "durationSec": null, "distanceKm": null, "personalBest": true }
      ]
    },
    {
      "exerciseId": 48, "name": "ランニング", "bodyPart": "cardio", "recordType": "time_distance", "isRunning": true,
      "personalBest": false,
      "sets": [ { "weightKg": null, "reps": null, "durationSec": 1800, "distanceKm": 5.000, "personalBest": false } ]
    }
  ],
  "images": [ { "id": 9, "url": "https://…", "sortOrder": 1 } ],
  "likeCount": 12, "likedByMe": true, "commentCount": 3,
  "mine": true
}
```

| 項目 | 内容 |
|---|---|
| `exercises` | 並び順のとおり。種目とセットをすべて返す（投稿カードの要約「60kg × 10回 × 3セット」は、画面がこの値から作る） |
| `personalBest`（種目） | その記録で、その種目の自己ベストを更新したか（[データ構造の自己ベスト](./database.md#自己ベストと自己ベスト更新の印)）。重量 × 回数の種目だけ true になりうる |
| `personalBest`（セット） | 自己ベストを出したセット（S-12 の 🏆）。種目の `personalBest` が true のとき、1つのセットだけ true |
| `name` | 他人の自分専用の種目も、名前はそのまま返す（F-14 の決まり：投稿に表示される名前としてだけ見える） |
| `mine` | 自分の記録か（`…` メニュー・「メモを書く」の見出しに使う） |
| 入れないもの | 消費カロリー（A-24 だけ）、ハッシュタグの一覧（画面がひとことから同じ決まりで取り出してリンクにする） |

- 種目は最大20件・セットは種目ごとに最大20件なので、1件の記録は大きくなりうる。タイムラインで返す量は、パフォーマンステストで確かめる。多すぎるときは、一覧では要約だけを返す形に変える
- 数値の `weightKg`・`distanceKm` は、JSON の数値（小数第3位まで）で返す

### Comment（A-60・A-61）

```json
{
  "id": 501, "author": { "username": "sato", … }, "body": "フォームきれいでした！",
  "createdAt": "…", "deletable": false,
  "replies": [
    { "id": 502, "author": { "username": "yamada", … }, "replyToUser": { "username": "sato", … },
      "body": "ありがとうございます！", "createdAt": "…", "deletable": true }
  ]
}
```

- `deletable` は、自分が書いたコメントか、自分の投稿へのコメントか（「削除」を出すかどうか）
- `replyToUser` は、返信先のユーザーが退会していれば `null`（画面は `@xxx` を出さない）

### Goal（自分の目標。A-110〜A-113）

```json
{
  "id": 7, "goalType": "volume", "name": "月間ランニング 100km",
  "period": "month", "metric": "distance", "targetValue": 100.000,
  "exercise": { "id": 48, "name": "ランニング" }, "bodyPart": null,
  "startedOn": "2026-11-01", "deadline": null, "achievedOn": null, "expired": false,
  "visibility": "followers", "cheerCount": 3,
  "progress": { "periodStart": "2026-11-01", "current": 35.200, "rate": 35 },
  "history": { "achievedPeriods": 6, "totalPeriods": 8 },
  "bodyWeight": null
}
```

| 項目 | 内容 |
|---|---|
| `targetValue`・`progress.current` | 種類で単位が決まる：頻度 ＝ 日数、量 ＝ kg（総挙上量）・秒ではなく**分**（運動時間。入力に合わせる）・km（距離）、重量・体重 ＝ kg |
| `progress.rate` | 0〜100 の整数（100% を超えても 100） |
| `progress.periodStart` | くり返しの目標の、今の週（月）の初日。それ以外は `null` |
| `history` | くり返しの目標の「これまで：達成 6週 / 8週」。それ以外は `null` |
| `bodyWeight` | 体重の目標のときだけ `{ "startKg": 75.0, "startRecordedOn": "2026-11-14", "currentKg": 73.0 }` |
| `expired` | 期限切れか（期限を過ぎて未達成） |

### PublicGoal（他人の公開した目標。A-84）

```json
{ "id": 7, "goalType": "volume", "name": "月間ランニング 100km", "period": "month", "metric": "distance",
  "targetValue": 100.000, "deadline": null, "achievedOn": null, "rate": 35, "cheerCount": 3, "cheeredByMe": false }
```

- **`progress.current`（もとになった記録の値）・`history`・`bodyWeight` は入れない**（F-106 の決まり）。自分のプロフィールで見るときも同じ形（応援ボタンの代わりに数だけ出す）

### Notification（A-120）

```json
{
  "id": 900, "type": "comment", "createdAt": "…", "read": false,
  "actor": { "username": "sato", … },
  "workout": { "id": 123, "bodyExcerpt": "今日は脚の日！…" },
  "comment": { "id": 501, "bodyExcerpt": "フォームきれいでした！" },
  "goal": null
}
```

- `type` は [データ構造の notifications](./database.md#notifications通知) の種類と同じ
- `bodyExcerpt` は先頭の30文字（超えたら「…」を付ける）
- 文言（「〇〇さんがあなたの記録にコメントしました」）は画面が `type` から作る

## 各 API の決まり

形からは分からない動き（エラーになる条件・並び順・何回呼んでも同じ結果になること など）を書く。前回の SNS アプリと同じものは「前回と同じ」とし、違いだけを書く。

### 認証・アカウント

#### A-01 新規登録・A-02 ログイン・A-04 再発行・A-05 ログアウト

- トークンの受け渡し（アクセストークンは JSON、リフレッシュトークンは Cookie）・ローテーション・使い回しの検知・ログインの試行回数の制限・パスワードの照合の同時実行の制限は、前回と同じ
- A-01 のリクエスト：`username`・`displayName`・`email`・`password`。メールアドレスは前後の空白を除き、小文字にそろえる
- A-01 は登録のあと、**確認メールの送信を、保存が確定したあとに別のスレッドで行う**。メールが送れなくても登録は成功にする（画面の「確認メールを再送」で送り直せる）
- A-02 はメールアドレスとパスワードでログインする（前回と同じ）

#### A-06 メールアドレスの確認

- リクエスト：`{ "token": "…" }`（リンクの `?token=` の値）。ログインしていなくても呼べる（別の端末でメールを開くことがあるため）
- 成功したら `{ "result": "verified" | "email_changed", "email": "…" }` を返す。画面は、ログイン中なら A-03 を取り直して案内を消す
- トークンが期限切れ・使用済み・不正なら 400（`INVALID_TOKEN`）
- メールアドレスの変更で、その間にほかの人が同じアドレスを登録していたら 409（`ALREADY_REGISTERED`）
- トークンを URL のクエリではなく本文で送るのは、サーバーのアクセスログに残さないため（画面の URL にはあるので、S-05 は `Referrer-Policy: no-referrer` にする。[画面仕様](./screens.md)）

#### A-07 確認メールの再送

- 今のメールアドレスに、新しい確認用のリンクを送る。前のリンクは使えなくなる。202 を返す
- 確認済みなら 409（`ALREADY_VERIFIED`）。1時間で5通を超えるなら 429（`TOO_MANY_EMAILS`）
- メールアドレスの変更（A-10）の確認待ちのときに呼ぶと、変更の確認用のリンクは使えなくなる（確認用のトークンは1人1つのため。[データ構造](./database.md#email_verification_tokensメールアドレスの確認用のトークン)）

#### A-08 パスワードのリセットの申請

- リクエスト：`{ "email": "…" }`。**登録されているかどうか・送信の上限を超えたかどうかに関係なく、いつも 202 を返す**（F-08 の決まり）。応答の速さで登録の有無が分からないよう、メールの送信は保存の確定後に別のスレッドで行い、応答を待たせない
- 登録されていれば、確認済みかどうかに関係なく再設定のリンクを送る

#### A-09 パスワードの再設定

- リクエスト：`{ "token": "…", "newPassword": "…" }`。成功したら 204
- トークンが期限切れ・使用済み・不正なら 400（`INVALID_TOKEN`）。新しいパスワードが規則に合わなければ 400（`VALIDATION_FAILED`）で、トークンは使用済みにしない（もう一度入力できる）
- 成功したら、そのユーザーのすべてのリフレッシュトークンを無効にする。ログイン状態にはしない（画面は S-01 へ）

#### A-10 メールアドレスの変更の申請

- リクエスト：`{ "newEmail": "…", "password": "…" }`。新しいアドレスに確認用のリンクを送り、202 を返す。リンクを開いて A-06 が成功するまで、今のアドレスのまま
- パスワードが違えば 400（`INCORRECT_PASSWORD`）。新しいアドレスが今のアドレスと同じなら 400（`VALIDATION_FAILED`）。ほかの人が使っていれば 409（`ALREADY_REGISTERED`）。上限を超えたら 429（`TOO_MANY_EMAILS`）

#### A-11 パスワードの変更

- リクエスト：`{ "currentPassword": "…", "newPassword": "…" }`
- 成功したら、そのユーザーのすべてのリフレッシュトークンを無効にしたうえで、**この端末用の新しいトークン**を A-02 と同じ形で返す（F-07 の「変更した端末だけはログインしたままにする」）
- 今のパスワードが違えば 400（`INCORRECT_PASSWORD`）

#### A-12 退会

- リクエスト：`{ "password": "…" }`（DELETE に本文を付ける）。成功したら 204 と、リフレッシュトークンの Cookie を消す `Set-Cookie`
- パスワードが違えば 400（`INCORRECT_PASSWORD`）
- ユーザーの行を消すと、関係する行はすべて消える（[データ構造の削除で一緒に消えるもの](./database.md#削除で一緒に消えるもの)）。S3 の画像は、確定後に消す

#### A-13 設定の変更

- リクエスト：`{ "weightUnit"?: "kg"|"lb", "distanceUnit"?: "km"|"mi", "isPrivate"?: boolean }`。送った項目だけを変える。変更後の Me を返す
- `isPrivate` を `false` にしたら、自分への承認待ちのリクエストをすべて承認する（F-53）。承認した人には「リクエストが承認された」の通知を作る
- 何回呼んでも同じ結果になる（同じ値を送っても、エラーにしない）

### 記録

#### A-20 記録の作成

`multipart/form-data` で、次の2つを送る。

| パート | 内容 |
|---|---|
| `data` | 記録の JSON（`Content-Type: application/json`） |
| `images` | 画像ファイル（0〜4個。選んだ順） |

```json
{
  "performedOn": "2026-11-14",
  "body": "今日は脚の日！ #脚トレ",
  "durationMin": 75,
  "visibility": "public",
  "weightUnit": "kg",
  "distanceUnit": "km",
  "exercises": [
    { "exerciseId": 16, "sets": [ { "weight": 60, "reps": 10 }, { "weight": 100, "reps": 5 } ] },
    { "exerciseId": 48, "sets": [ { "durationSec": 1800, "distance": 5.00 } ] }
  ]
}
```

- 入力のチェックは [機能要件の記録の項目](./features.md#トレーニング記録投稿) のとおり。セットに入れる値は種目の記録方式で決まり、記録方式に合わない値（重量 × 回数の種目に `durationSec` があるなど）は 400
- `exerciseId` は、最初から用意された種目か、自分専用の種目だけ。他人の自分専用の種目・存在しない種目は 400（`VALIDATION_FAILED`、`field` は `exercises[0].exerciseId`）
- 実施日が未来（日本時間の今日より後）なら 400
- ひとことは前後の空白・改行を除き、`\r\n` を `\n` にそろえてから数える（前回と同じ）。ハッシュタグは、ここで取り出して保存する
- 画像は JPEG・PNG・WebP で、1枚 2MB まで。形式はファイルの中身（先頭のバイト列）で判定する。リクエスト全体は 10MB まで（`spring.servlet.multipart`）
- 画像は S3 の `workouts/{年}/{月}/{UUID}.{拡張子}` に保存してから DB に保存する。DB の保存が失敗したら、保存した画像を消す。S3 に保存できなければ 500（`IMAGE_UPLOAD_FAILED`）で、記録も作らない（前回と同じ）
- 成功したら 201 で Workout を返す。画面は `personalBest` が true の種目があれば「🏆 〇〇の自己ベストを更新しました！」と出す
- **保存の確定後**に、別のスレッドで目標の達成を判定する（F-104）。判定の失敗は、記録の作成の結果に影響しない
- 画像の位置情報（EXIF）は、画面が Canvas で書き出し直すときに消える。API を直接呼んだときの扱いは、実装のときに決める（[未決定事項](#未決定事項)）

#### A-21 記録の詳細

- 見てはいけない・存在しないときは 404（`WORKOUT_NOT_FOUND`）
- コメントは含めない（A-60 で取る）

#### A-22 記録の編集

- A-20 と同じ形。`data` に、残す画像の ID を並び順に入れた `keepImageIds` を足す。新しい画像は `images` で送り、残す画像のあとに並べる。合わせて4枚まで
- `keepImageIds` にない画像は、確定後に S3 から消す
- 他人の記録は 403（`FORBIDDEN`）。見てはいけない記録は 404
- 種目とセットは、送られた内容で置き換える（[データ構造](./database.md#workout_sets記録のセット)）。`edited_at` に今の時刻を入れる
- 確定後に目標の達成を判定する（A-20 と同じ）

#### A-23 記録の削除

- 他人の記録は 403。画像は確定後に S3 から消す（前回と同じ）。204 を返す

#### A-24 記録の消費カロリー

- **本人の記録だけ**。他人の記録は、見てよい記録でも 404（`WORKOUT_NOT_FOUND`）にする（消費カロリーがあることも知らせない）
- レスポンス

  ```json
  { "status": "ok", "totalKcal": 520, "aerobicKcal": 210, "strengthKcal": 310, "strengthIncluded": true, "bodyWeightRecordedOn": "2026-11-13" }
  ```

  | `status` | 場面 | 画面の表示 |
  |---|---|---|
  | `ok` | 計算できた | 「消費カロリーの目安 約 520kcal」。`strengthIncluded` が false（トレーニング時間がない）なら、「有酸素の分だけの目安です」と書き添える |
  | `no_body_record` | 実施日以前の体重の記録がない | 「体重を記録すると消費カロリーの目安が表示されます」。数値の項目は `null` |

- 体重の値そのものは返さない（記録した日だけ）

### 種目・テンプレート

#### A-30 種目の検索

- `q`（任意、30文字まで）：名前の部分一致。NFKC で正規化し小文字にしてから比べる（全角・半角、大文字・小文字を区別しない）
- `bodyPart`（任意）：部位で絞り込む
- 最初から用意された種目と、**自分の**自分専用の種目だけを返す。ページングはしない（最大 58 ＋ 100 件）
- 並び順：部位の順（胸・背中・脚・肩・腕・腹・全身・有酸素）→ 最初から用意された種目の順 → 自分専用の種目の名前の順
- 各要素：`{ "id", "name", "bodyPart", "recordType", "isRunning", "custom" }`（`custom` は自分専用の種目か）

#### A-31 最近使った種目

- 自分の記録で使った種目を、最後に使った記録の新しい順（実施日・投稿日時）に10件まで。形は A-30 と同じ

#### A-32〜A-34 自分専用の種目

- A-32 のリクエスト：`{ "name", "bodyPart", "recordType" }`。METs は部位と記録方式から決める（[データ構造](./database.md#exercises種目)）。201 で種目を返す
- 名前が、最初から用意された種目か自分の種目と同じなら 409（`EXERCISE_NAME_TAKEN`）。100件あれば 409（`EXERCISE_LIMIT_EXCEEDED`）
- A-33 のリクエスト：`{ "name", "bodyPart" }`（記録方式は変えられない）。名前の重複は A-32 と同じ
- A-34：記録・目標で使っていれば 409（`EXERCISE_IN_USE`）。テンプレートからは外す。204
- 他人の種目・最初から用意された種目は 404（`EXERCISE_NOT_FOUND`）

#### A-40〜A-44 テンプレート

- A-40 は自分のテンプレートを更新日時の新しい順にすべて返す（20件まで）。各要素は名前・種目の名前の一覧・種目の数（S-14 の表示に使う）
- A-41 は種目とセットを含めて返す。セットの値は Workout と同じく kg・km・秒
- A-42・A-43 のリクエスト：`{ "name", "weightUnit", "distanceUnit", "exercises": [...] }`（`exercises` は A-20 と同じ形）。種目・セットの決まりは記録と同じ
- 20件あれば 409（`TEMPLATE_LIMIT_EXCEEDED`）
- 記録の詳細から「テンプレートとして保存」するときは、画面が A-21 で取った記録の種目とセットを、A-42 で送る（専用の API は作らない）
- 他人のテンプレートは 404（`TEMPLATE_NOT_FOUND`）

### タイムライン・検索

#### A-50・A-51 タイムライン

- 投稿日時の新しい順に20件（F-20・F-22）。A-50 は自分と承認済みのフォロー相手の、見てよい記録。A-51 は全員の、見てよい記録
- カーソルには、最後に返した記録の投稿日時と ID が入る

#### A-52・A-53 新しい投稿の件数

- 前回と同じ。`since` は画面が最後に取った1ページ目の一番新しい記録の ID。それより後の、見てよい記録の件数を返す（自分の記録は数えない。100件で数えるのをやめる）

#### A-54 投稿の検索

- `q`（1〜50文字。前後の空白を除く）・`tag`（`#` なし。NFKC と小文字にそろえて探す）・`exerciseId` を、1つ以上指定する。どれもなければ 400
- 指定した条件をすべて満たす、見てよい記録を、投稿日時の新しい順に20件（F-25〜F-27）
- `q` の `%` `_` `\` は普通の文字として探す。英字の大文字・小文字は区別しない（[データ構造の pg_bigm](./database.md#日本語の部分一致検索pg_bigm)）
- `q` が `#` で始まるときにハッシュタグとして扱うのは画面の役目（`tag` に移して呼ぶ）

#### A-55 自分がよく使うハッシュタグ

- 自分の直近50件の記録で使ったハッシュタグを、使った回数の多い順（同じなら最近使った順）に10個まで返す

### コメント・ナイストレーニング

#### A-60 コメント一覧

- 返信でないコメントを古い順に20件ずつ。それぞれに、返信を古い順に**すべて**入れて返す（S-12 は返信を折りたたまない）
- 返信が多い投稿で応答が大きくなりすぎるときは、パフォーマンステストの結果を見て、返信だけを別の API に分ける
- 投稿が見てはいけない・存在しないなら 404（`WORKOUT_NOT_FOUND`）

#### A-61 コメント・返信の投稿

- リクエスト：`{ "body": "…", "replyToCommentId"?: 501 }`
  - `replyToCommentId` なし：投稿へのコメント
  - 返信でないコメントの ID：そのコメントへの返信（返信元 ＝ そのコメント、返信先 ＝ その人）
  - 返信の ID：その返信と同じ返信元への返信（返信元 ＝ その返信の返信元、返信先 ＝ その返信を書いた人）。返信は1段だけになる（F-33）
- 本文は前後の空白・改行を除いて1〜140文字
- `replyToCommentId` のコメントが、その投稿のコメントでない・存在しなければ 404（`COMMENT_NOT_FOUND`）
- 201 で、作ったコメントと、投稿後のコメント数（`commentCount`。返信を含む）を返す
- 「自分のみ」の投稿には本人だけがコメントできる（ほかの人には見えないので、どのみち 404 になる）
- 通知は確定後に作る（コメント → 投稿者、返信 → 返信先の人。同じ人なら1件。[データ構造の notifications](./database.md#notifications通知)）

#### A-62 コメントの削除

- 削除できるのは、コメントを書いた本人と、投稿者本人（前回は書いた本人だけ）。それ以外は 403（`FORBIDDEN`）
- 返信元を消すと、返信も消える。200 で削除後のコメント数 `{ "commentCount": 2 }` を返す

#### A-63・A-64 ナイストレーニング

- 前回のいいねと同じく、**何回呼んでも結果が同じ**。200 で `{ "likeCount": 13, "likedByMe": true }` を返す
- 見てはいけない記録は 404。付けたら投稿者に通知を作り、取り消したら通知を消す（自分の記録なら作らない）

### フォロー・プロフィール

#### A-70・A-71 フォロー・フォロー解除

- A-70 は、相手が鍵アカウントなら承認待ち、そうでなければ承認済みのフォローを作り、`{ "followStatus": "requested" | "following" }` を返す。すでにフォロー中・リクエスト中なら、今の状態を返す（何回呼んでも同じ）
- A-71 は、フォロー中でもリクエスト中でも、行を消して `{ "followStatus": "none" }` を返す。どちらでもなくてもエラーにしない
- 自分自身は 400（`CANNOT_FOLLOW_SELF`）。ユーザーがいなければ 404（`USER_NOT_FOUND`）
- 通知：フォロー → `follow`、リクエスト → `follow_request`。取り消したら、その通知を消す

#### A-72・A-73 フォロー中・フォロワー一覧

- 承認済みだけを、フォローした日時の新しい順に20件（前回と同じ）。各要素は UserSummary に、自己紹介と、**自分から見た**フォローの状態 `followStatus`（`none`・`requested`・`following`）、自分自身かどうか `me` を足したもの（[ユーザー一覧の行](./screens.md#ユーザー一覧の行共通)のボタンに使う。A-74・A-83 も同じ形）
- 鍵アカウントの一覧を、本人でも承認済みのフォロワーでもない人が見ようとしたら 403（`PRIVATE_ACCOUNT`）

#### A-74〜A-76 フォローのリクエスト

- A-74 は、自分へのリクエストを新しい順に20件
- A-75 は承認して、相手に `follow_accepted` の通知を作り、`follow_request` の通知を消す。204
- A-76 は拒否してリクエストを消す（相手には通知しない）。`follow_request` の通知も消す。204
- そのユーザーからのリクエストがなければ 404（`FOLLOW_REQUEST_NOT_FOUND`。取り消されたあとに押したときなど）

#### A-80 プロフィール

```json
{
  "username": "yamada", "displayName": "山田太郎", "iconUrl": null, "bio": "…", "isPrivate": true,
  "followingCount": 12, "followerCount": 8,
  "me": false, "followStatus": "following", "canViewRecords": true
}
```

- `followStatus` は自分から相手へのフォローの状態
- `canViewRecords` は、記録・公開した目標・フォロー一覧を見られるか（本人・鍵アカウントでない・承認済みのフォロワー）。false なら、画面は「🔒 このアカウントの記録は、承認されたフォロワーだけが見られます」を出し、A-81・A-84 を呼ばない
- 鍵アカウントでも、上の項目（アイコン・表示名・自己紹介・数）は返す（F-50 の決まり）
- `{username}` は大文字・小文字を区別しない。いなければ 404（`USER_NOT_FOUND`）

#### A-81 そのユーザーの記録一覧

- 見てよい記録を、**実施日**の新しい順（同じ日は投稿日時の新しい順）に20件（F-62）。カーソルには実施日・投稿日時・ID が入る
- S-13 の「前回の記録」（自分の直近10件）にも使う（`/api/users/{自分}/workouts` の1ページ目の先頭10件）
- 鍵アカウントで見られないときは、閲覧の条件で0件になる（エラーにしない）

#### A-82 プロフィールの編集

- 前回と同じ。ただし、アイコンは JPEG・PNG・WebP で 2MB まで（画面で 512px 四方に縮小してから送る）。保存先は `icons/{UUID}.{拡張子}`

#### A-83 ユーザー検索

- 前回と同じ（キーワードの扱い・並び順・カーソル）。ただし、各要素は A-72 と同じ形（`followStatus` で「リクエスト中」も出せる）
- 部分一致は pg_bigm の索引を使う（前回の pg_trgm から変更。2文字のキーワードでも索引が効く）

#### A-84 公開した目標

- そのユーザーの、見てよい目標（公開範囲の判定は投稿と同じ）を PublicGoal の形で返す。達成済み以外を先に、作った日の新しい順。ページングはしない（最大でも10件 ＋ 達成済み）
- 達成済みの目標は、達成日の新しい順に最大10件まで付ける

### 集計

#### 共通

- 本人の記録だけを、公開範囲に関係なく集計する。日付・期間は日本時間
- 期間を表すパラメータ（`date`・`month`）がなければ、今週・今月・今年にする。`date` は期間の中のどの日でもよく、サーバーがその週（月曜〜日曜）・月・年に直す
- 未来の期間を指定してもエラーにしない（0 を返す。画面は未来に移動させない）
- 前の期間との差は、サーバーが前の期間の値も返し、画面が引き算する（差の正負の記号・色は画面で決める）

#### A-90 週間の集計

```json
{
  "from": "2026-11-10", "to": "2026-11-16",
  "summary":  { "trainingDays": 4, "totalVolumeKg": 12450.000, "totalDurationSec": 5700, "totalDistanceKm": 15.000,
                "caloriesKcal": 2150, "caloriesExcludedWorkoutCount": 0 },
  "previous": { "trainingDays": 3, "totalVolumeKg": 11650.000, … },
  "daily": [ { "date": "2026-11-10", "volumeKg": 3200.000, "durationSec": 0 }, … 7日分 ],
  "bodyParts": [ { "bodyPart": "chest", "setCount": 12 }, … ]
}
```

- `daily` は記録のない日も 0 で7日分返す（グラフの棒の位置がずれないように）
- `caloriesKcal` は、計算できた記録の合計。`caloriesExcludedWorkoutCount` が 1 以上なら、画面は「※ 体重の記録がない日を除く」と出す。計算できた記録が1件もなければ `caloriesKcal` は `null`
- `bodyParts` はセット数の多い順。割合は画面が計算する

#### A-91 月間の集計

- `month` は `YYYY-MM`。形は A-90 と同じで、`daily` の代わりに `weekly`（その月に含まれる週ごと。`{ "weekStart", "volumeKg", "durationSec", "distanceKm", "trainingDays" }`）を返す。月をまたぐ週は、その月の日だけを数える

#### A-92・A-93 カレンダー

- A-92 は `{ "month": "2026-11", "dates": ["2026-11-04", "2026-11-06", …] }`（記録がある日だけ）
- A-93 は、その日の自分の記録を投稿日時の順にすべて返す（Workout の配列。1日の件数は少ないので、ページングしない）

#### A-94 ランニングの集計

- `period` は `week`・`month`・`year`（必須）
- レスポンス

  ```json
  {
    "from": "2026-11-01", "to": "2026-11-30",
    "summary":  { "distanceKm": 45.200, "durationSec": 16270, "runCount": 8, "avgPaceSecPerKm": 360 },
    "previous": { … },
    "bestPace":   { "workoutId": 120, "performedOn": "2026-11-08", "distanceKm": 10.000, "durationSec": 3120, "paceSecPerKm": 312 },
    "longestRun": { "workoutId": 101, "performedOn": "2026-11-03", "distanceKm": 15.000, "durationSec": 5720, "paceSecPerKm": 381 },
    "monthly": []
  }
  ```

- `avgPaceSecPerKm` は、距離のあるセットの時間の合計 ÷ 距離の合計を、秒に四捨五入したもの。距離のある記録がなければ `null`
- `bestPace` は 1km 以上のセットから（F-83）。対象がなければ `null`。`longestRun` も、なければ `null`
- `monthly` は `period=year` のときだけ、12か月分の `{ "month": "2026-01", "distanceKm": … }`（記録のない月も 0）。それ以外は空の配列

#### A-95・A-96 種目ごとの推移

- A-95 は、自分が1回以上記録した種目を、部位ごとに、最後に記録した日つきで返す（`{ "exercise": {…}, "lastPerformedOn": "…" }`）
- A-96 の `range` は `3m`・`1y`・`all`（なければ `3m`）
- A-96 のレスポンス

  ```json
  {
    "exercise": { "id": 1, "name": "ベンチプレス", "recordType": "weight_reps", … },
    "personalBest": { "weightKg": 100.000, "performedOn": "2026-10-20", "workoutId": 98 },
    "bestE1rm":     { "e1rmKg": 112.500, "performedOn": "2026-11-02", "workoutId": 104 },
    "points": [
      { "date": "2026-09-01", "workoutId": 80, "maxWeightKg": 85.000, "maxE1rmKg": 95.200, "maxWeightReps": 5,
        "totalReps": null, "maxReps": null, "totalDurationSec": null, "totalDistanceKm": null, "paceSecPerKm": null }
    ]
  }
  ```

- `points` は実施日の古い順（グラフの順）。記録方式で使わない項目は `null`。`workoutId` はその日の一番よい値を出した記録（S-22 の一覧の行から S-12 へ移るため）
- `personalBest`・`bestE1rm` は全期間の値（`range` に関係ない）。重量 × 回数の種目でなければ `null`
- 自分が記録していない種目・存在しない種目は、`points` を空にして返す（種目そのものが見えない他人の自分専用の種目は 404）

#### A-97 今週のひとこと

- `{ "trainingDays": 3, "frequencyGoal": { "goalId": 5, "name": "週4日トレーニング", "targetValue": 4, "rate": 75 } }`
- `frequencyGoal` は、毎週の頻度の目標のうち最初に作ったもの。なければ `null`
- ホームを開くたびに呼ぶので、週間の集計（A-90）より軽い専用の API にする

### 体重

#### A-100 体重の記録

- パスの `{date}` の日の記録を作るか上書きする（何回呼んでも同じ結果）。リクエスト：`{ "weight": 73.0, "weightUnit": "kg", "bodyFatPct": 18.5 }`
- 刻み（kg は 0.1、lb は 0.2）と範囲は [機能要件の体重](./features.md#体重の記録) のとおり。未来の日付は 400
- 作ったら 201、上書きしたら 200 で、`{ "recordedOn", "weightKg", "bodyFatPct" }` を返す
- 確定後に、体重の目標の達成を判定する

#### A-101〜A-103

- A-101 は日付の新しい順に30件ずつ（S-25）
- A-102 はその日の記録を消す。なければ 404（`BODY_RECORD_NOT_FOUND`）。204
- A-103 の `range` は `3m`・`1y`。`{ "points": [ { "date", "weightKg", "bodyFatPct" } ] }` を日付の古い順に返す

### 目標

#### A-110 目標の一覧

- `status=active`（初期値）：達成済み以外の目標。期限切れでないものを、期限・期間が近い順に。期限切れは最後に並べる（S-23）
- `status=achieved`：達成した重量・体重の目標（達成日の新しい順）と、くり返しの目標（達成した期間が1回以上あるもの）
- 達成率は、呼ぶたびに記録から計算する

#### A-111・A-113 目標の作成・編集

- リクエスト

  ```json
  { "goalType": "volume", "name": "月間ランニング 100km", "period": "month", "metric": "distance",
    "targetValue": 100, "weightUnit": "kg", "distanceUnit": "km",
    "exerciseId": null, "bodyPart": null, "deadline": null, "visibility": "followers" }
  ```

- 種類ごとに必要な項目・使わない項目は [データ構造の goals](./database.md#goals目標) のとおり。使わない項目に値があれば 400
- `targetValue` の単位は、重量・体重・総挙上量は `weightUnit`、距離は `distanceUnit`、運動時間は分、頻度は日数
- 体重の目標で `visibility` が `private` 以外なら 400。体重の記録が1件もなければ 400（`BODY_RECORD_REQUIRED`）
- 期限が今日以前なら 400
- 達成済み以外の目標が10件あれば 409（`GOAL_LIMIT_EXCEEDED`。作成のときだけ）
- A-113 では `goalType` を変えられない（送った値が今の種類と違えば 400）。目標値などを変えても、過去の達成の記録は消さない
- 作成・編集のあとに、達成の判定を確定後に行う（すでに達成している目標値にしたときのため）
- 201（作成）・200（編集）で Goal を返す

#### A-114 目標の削除

- 応援・達成の記録・関係する通知も消える。204

#### A-115 達成の履歴

- くり返しの目標の、達成した期間の一覧（`{ "periodStart", "achievedOn" }` を新しい順）と、目標を作ってからの期間の数。くり返しでない目標は 400

#### A-116・A-117 応援

- ナイストレーニングと同じく、何回呼んでも結果が同じ。200 で `{ "cheerCount": 4, "cheeredByMe": true }`
- 見てはいけない目標は 404（`GOAL_NOT_FOUND`）。自分の目標は 400（`CANNOT_CHEER_OWN_GOAL`）
- 応援したら持ち主に `goal_cheer` の通知を作り、取り消したら消す

### 通知

#### A-120 通知一覧

- 自分への通知を新しい順に20件。見られなくなった投稿・コメントについての通知は除く（F-110 の決まり）
- 一覧を取っただけでは既読にしない（A-122 で既読にする）

#### A-121 未読の数

- `{ "unreadCount": 3, "followRequestCount": 1 }`
- `unreadCount` は、A-120 で表示される通知のうち未読のもの（見られなくなった通知は数えない）。100 で数えるのをやめる（画面は「99+」）
- `followRequestCount` は、自分へのフォローのリクエストの数（アイコンのメニューに出す）
- 画面は60秒ごとと、ブラウザのタブが表に戻ったときに呼ぶ

#### A-122 既読にする

- リクエスト：`{ "ids": [900, 899, …] }`（画面に表示した通知の ID。最大50件）。自分の通知で未読のものだけを既読にする（ほかの人の通知の ID・既読の ID は無視する）。204
- 何回呼んでも同じ結果になる

### A-130 画面で起きたエラーの報告

前回と同じ。

## 未決定事項

| 項目 | 内容 |
|---|---|
| 画像の位置情報（EXIF） | 画面は Canvas で書き出し直すので位置情報は消えるが、API を直接呼んだときに残る。サーバーで消すか、EXIF つきの画像を受け付けないかを、実装のときに決める |
| タイムラインの応答の大きさ | 記録の種目とセットをすべて返す。パフォーマンステストで大きすぎると分かったら、一覧では要約だけを返す形に変える |
| コメントの返信の量 | 返信をすべて返す。多すぎると分かったら、返信を別の API に分ける |
