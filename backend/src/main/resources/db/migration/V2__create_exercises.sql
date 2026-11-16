-- 種目（docs/database.md「exercises」）と、最初から用意する58種目（docs/database.md「種目の初期データ」）
CREATE TABLE exercises (
  id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  owner_id    BIGINT       REFERENCES users (id) ON DELETE CASCADE,
  name        VARCHAR(30)  NOT NULL CHECK (LENGTH(name) >= 1),
  name_key    VARCHAR(30)  NOT NULL,
  body_part   VARCHAR(10)  NOT NULL
    CHECK (body_part IN ('chest', 'back', 'legs', 'shoulders', 'arms', 'abs', 'full_body', 'cardio')),
  record_type VARCHAR(20)  NOT NULL CHECK (record_type IN ('weight_reps', 'reps', 'time_distance')),
  mets        NUMERIC(3,1) NOT NULL CHECK (mets > 0),
  is_running  BOOLEAN      NOT NULL DEFAULT false
    CHECK (NOT is_running OR (owner_id IS NULL AND record_type = 'time_distance')),
  sort_order  SMALLINT     NOT NULL DEFAULT 0,
  created_at  TIMESTAMPTZ  NOT NULL,
  updated_at  TIMESTAMPTZ  NOT NULL
);

-- 同じ名前の禁止（最初から用意された種目どうし・自分専用の種目どうし）。
-- 2つ目は、自分専用の種目の一覧と検索、退会のときの CASCADE にも使う
CREATE UNIQUE INDEX uq_exercises_preset_name_key ON exercises (name_key) WHERE owner_id IS NULL;
CREATE UNIQUE INDEX uq_exercises_owner_name_key ON exercises (owner_id, name_key) WHERE owner_id IS NOT NULL;

-- 最初から用意する種目。部位の中では、この順に並べる（sort_order）。
-- name_key は、アプリ（Java の Normalizer）と同じく NFKC で正規化して小文字にした名前
INSERT INTO exercises (body_part, name, name_key, record_type, mets, is_running, sort_order, created_at, updated_at)
SELECT body_part, name, LOWER(NORMALIZE(name, NFKC)), record_type, mets, is_running, sort_order, now(), now()
FROM (VALUES
  ('chest', 'ベンチプレス', 'weight_reps', 6.0, false, 1),
  ('chest', 'インクラインベンチプレス', 'weight_reps', 6.0, false, 2),
  ('chest', 'ダンベルプレス', 'weight_reps', 3.5, false, 3),
  ('chest', 'ダンベルフライ', 'weight_reps', 3.5, false, 4),
  ('chest', 'チェストプレス', 'weight_reps', 3.5, false, 5),
  ('chest', 'ケーブルクロスオーバー', 'weight_reps', 3.5, false, 6),
  ('chest', 'ディップス', 'reps', 3.8, false, 7),
  ('chest', '腕立て伏せ', 'reps', 3.8, false, 8),
  ('back', 'デッドリフト', 'weight_reps', 6.0, false, 1),
  ('back', 'ベントオーバーロウ', 'weight_reps', 6.0, false, 2),
  ('back', 'ラットプルダウン', 'weight_reps', 3.5, false, 3),
  ('back', 'シーテッドロウ', 'weight_reps', 3.5, false, 4),
  ('back', 'ワンハンドダンベルロウ', 'weight_reps', 3.5, false, 5),
  ('back', '懸垂', 'reps', 3.8, false, 6),
  ('back', 'バックエクステンション', 'reps', 3.5, false, 7),
  ('legs', 'スクワット', 'weight_reps', 6.0, false, 1),
  ('legs', 'フロントスクワット', 'weight_reps', 6.0, false, 2),
  ('legs', 'ルーマニアンデッドリフト', 'weight_reps', 6.0, false, 3),
  ('legs', 'レッグプレス', 'weight_reps', 3.5, false, 4),
  ('legs', 'レッグエクステンション', 'weight_reps', 3.5, false, 5),
  ('legs', 'レッグカール', 'weight_reps', 3.5, false, 6),
  ('legs', 'ランジ', 'weight_reps', 3.5, false, 7),
  ('legs', 'ヒップスラスト', 'weight_reps', 3.5, false, 8),
  ('legs', 'カーフレイズ', 'weight_reps', 3.5, false, 9),
  ('shoulders', 'ショルダープレス', 'weight_reps', 6.0, false, 1),
  ('shoulders', 'ダンベルショルダープレス', 'weight_reps', 3.5, false, 2),
  ('shoulders', 'サイドレイズ', 'weight_reps', 3.5, false, 3),
  ('shoulders', 'フロントレイズ', 'weight_reps', 3.5, false, 4),
  ('shoulders', 'リアレイズ', 'weight_reps', 3.5, false, 5),
  ('shoulders', 'アップライトロウ', 'weight_reps', 3.5, false, 6),
  ('shoulders', 'シュラッグ', 'weight_reps', 3.5, false, 7),
  ('arms', 'バーベルカール', 'weight_reps', 3.5, false, 1),
  ('arms', 'ダンベルカール', 'weight_reps', 3.5, false, 2),
  ('arms', 'ハンマーカール', 'weight_reps', 3.5, false, 3),
  ('arms', 'ナローベンチプレス', 'weight_reps', 6.0, false, 4),
  ('arms', 'トライセプスエクステンション', 'weight_reps', 3.5, false, 5),
  ('arms', 'トライセプスプッシュダウン', 'weight_reps', 3.5, false, 6),
  ('abs', 'クランチ', 'reps', 2.8, false, 1),
  ('abs', 'レッグレイズ', 'reps', 2.8, false, 2),
  ('abs', 'アブローラー', 'reps', 3.8, false, 3),
  ('abs', 'ケーブルクランチ', 'weight_reps', 3.5, false, 4),
  ('abs', 'プランク', 'time_distance', 2.8, false, 5),
  ('full_body', 'パワークリーン', 'weight_reps', 6.0, false, 1),
  ('full_body', 'ケトルベルスイング', 'weight_reps', 8.0, false, 2),
  ('full_body', 'バーピー', 'reps', 8.0, false, 3),
  ('full_body', 'サーキットトレーニング', 'time_distance', 8.0, false, 4),
  ('full_body', 'ヨガ', 'time_distance', 2.5, false, 5),
  ('cardio', 'ランニング', 'time_distance', 8.3, true, 1),
  ('cardio', 'ジョギング', 'time_distance', 7.0, true, 2),
  ('cardio', 'トレッドミル', 'time_distance', 8.3, true, 3),
  ('cardio', 'ウォーキング', 'time_distance', 3.5, false, 4),
  ('cardio', 'エアロバイク', 'time_distance', 6.8, false, 5),
  ('cardio', 'サイクリング', 'time_distance', 7.5, false, 6),
  ('cardio', '水泳', 'time_distance', 6.0, false, 7),
  ('cardio', 'ローイングマシン', 'time_distance', 7.0, false, 8),
  ('cardio', 'クロストレーナー', 'time_distance', 5.0, false, 9),
  ('cardio', 'ステアクライマー', 'time_distance', 9.0, false, 10),
  ('cardio', '縄跳び', 'time_distance', 11.8, false, 11)
) AS seed (body_part, name, record_type, mets, is_running, sort_order)
ORDER BY CASE body_part
  WHEN 'chest' THEN 1 WHEN 'back' THEN 2 WHEN 'legs' THEN 3 WHEN 'shoulders' THEN 4
  WHEN 'arms' THEN 5 WHEN 'abs' THEN 6 WHEN 'full_body' THEN 7 ELSE 8 END, sort_order;
