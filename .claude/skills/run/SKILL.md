---
name: run
description: FIT LOG（Spring Boot バックエンド + Vite/React フロントエンド + pg_bigm 入りの PostgreSQL）の開発サーバーを起動する。ポートが競合したら占有しているプロセスを止め、決まったポートで起動し直す（別のポートに逃がさない）。前回の SNS アプリとは同時に起動しない。
---

# アプリの起動の手順

## 決まったポート（変えない）

| 部品 | ポート | 根拠 |
|---|---|---|
| PostgreSQL（pg_bigm 入り） | `5432` | `.env` の `POSTGRES_PORT`（`docker-compose.yml`） |
| バックエンド（Spring Boot） | `8080` | `application.yml` に `server.port` の指定なし（Spring Boot の既定値） |
| フロントエンド（Vite） | `5173` | `vite.config.ts` に `server.port` の指定なし（Vite の既定値） |
| Mailpit（メールの確認） | SMTP `1025`・画面 `8025` | Mailpit の既定値（`docker-compose.yml`） |

- フロントエンドは `/api` を Vite のプロキシでバックエンド（`localhost:8080`）に中継する
- LocalStack（4566）は、使う機能を作るときに docker-compose に足す（docs/test-plan.md 8.1）

## 起動する前に：前回の SNS アプリを止める

ポートが同じなので、SNS アプリ（`../SNSアプリ`）のコンテナとサーバーが動いていたら止める（CLAUDE.md で、同時に起動しないと決めている）。

```bash
docker ps --format '{{.Names}}\t{{.Ports}}'          # sns-app-* が動いていないか
(cd ../SNSアプリ && docker compose stop)              # 動いていたら止める（データは消えない）
```

## 起動のコマンド

```bash
# 0. 初回だけ：.env を用意し、JWT_SECRET に openssl rand -base64 48 の結果を入れる（空だとバックエンドが起動しない）
cp .env.example .env

# 1. DB とメール（初回は db/Dockerfile のビルドで数分かかる）
docker compose up -d --wait postgres mailpit

# 2. バックエンド（リポジトリ直下から）。Java 25 は Homebrew の openjdk@25（keg-only なので JAVA_HOME を指定する）
cd backend && JAVA_HOME=$(brew --prefix openjdk@25)/libexec/openjdk.jdk/Contents/Home ./gradlew bootRun

# 3. フロントエンド（別のターミナルで、リポジトリ直下から）。初回は npm ci で依存関係を入れる
cd frontend && npm run dev
```

- バックエンドは起動のときに Flyway でテーブルを作る（`backend/src/main/resources/db/migration`）
- DB を作り直したいとき：`docker compose down -v && docker compose up -d --wait postgres mailpit`（**データがすべて消える**）
- 新規登録・確認メールの再送で送ったメールは、Mailpit の画面（http://localhost:8025）で見る。リンクはフロントエンドの `/verify-email?token=…`

## ポートが競合したとき（必ず守る）

**空いている別のポートに逃がして起動しない。** 決まったポートで起動できる状態にしてから起動する。

1. ポートを使っているプロセスを調べる

   ```bash
   lsof -ti tcp:8080   # バックエンド
   lsof -ti tcp:5173   # フロントエンド
   lsof -ti tcp:5432   # PostgreSQL
   lsof -ti tcp:1025 -ti tcp:8025   # Mailpit
   ```

2. このプロジェクトの古いプロセス（止め忘れた bootRun・vite・コンテナ）なら止める

   ```bash
   lsof -ti tcp:8080 | xargs kill
   ```

   - 5432 は、Docker のコンテナが使っていることが多い。`docker ps` でどのプロジェクトのコンテナかを確かめる。SNS アプリのものなら上の手順で止める。それ以外のプロジェクトのものは、止める前にユーザーに確認する
   - 何のプロセスか分からないときは、kill する前にユーザーに確認する
   - `kill` で終わらないときだけ `kill -9` を考える

3. ポートが空いたことを確かめてから、同じポートで起動し直す

## 起動の確認

- DB・メール：`docker compose ps`（`postgres` が `healthy`、`mailpit` が `running`）。Mailpit の画面は `curl -sf http://localhost:8025`
- フロントエンド：`curl -sf http://localhost:5173` か、ブラウザで開く
- バックエンド（フロントエンドの中継を通す）：ログイン以外の API はログインが必要。確認用のアカウントを作り（2回目からはログイン）、返ったトークンで呼ぶ

  ```bash
  # 新規登録（2回目からは 409 になるので、/api/auth/login に email と password を送る）
  curl -s -X POST http://localhost:5173/api/auth/signup -H 'Content-Type: application/json' \
    -d '{"username":"demo_user","displayName":"デモ","email":"demo@example.com","password":"password123"}'
  TOKEN=$(curl -s -X POST http://localhost:5173/api/auth/login -H 'Content-Type: application/json' \
    -d '{"email":"demo@example.com","password":"password123"}' | jq -r .accessToken)
  curl -s http://localhost:5173/api/auth/me -H "Authorization: Bearer $TOKEN"
  curl -s http://localhost:5173/api/exercises -H "Authorization: Bearer $TOKEN" | head -c 200
  ```
