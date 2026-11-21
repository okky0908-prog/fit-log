-- トレーニング記録（docs/database.md「workouts」「workout_exercises」「workout_sets」）。
-- ひとことの検索の索引（pg_bigm）は、投稿の検索を作るときに V13 で足す
CREATE TABLE workouts (
  id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  performed_on DATE         NOT NULL,
  body         VARCHAR(280) NOT NULL DEFAULT '',
  duration_min SMALLINT     CHECK (duration_min BETWEEN 1 AND 1440),
  visibility   VARCHAR(10)  NOT NULL DEFAULT 'public'
    CHECK (visibility IN ('public', 'followers', 'private')),
  created_at   TIMESTAMPTZ  NOT NULL,
  updated_at   TIMESTAMPTZ  NOT NULL,
  edited_at    TIMESTAMPTZ
);

-- フォロー中タイムライン（自分とフォロー相手の投稿を新しい順）。退会のときの CASCADE にも使う
CREATE INDEX ix_workouts_user_created ON workouts (user_id, created_at DESC, id DESC);
-- 全体タイムライン・新しい投稿の件数
CREATE INDEX ix_workouts_created ON workouts (created_at DESC, id DESC);
-- プロフィールの投稿一覧（実施日の新しい順）・集計・最近使った種目
CREATE INDEX ix_workouts_user_performed
  ON workouts (user_id, performed_on DESC, created_at DESC, id DESC);

CREATE TABLE workout_exercises (
  id          BIGINT   GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workout_id  BIGINT   NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
  -- NO ACTION：記録で使っている種目は消せない。RESTRICT にしないのは、退会のとき同じ DELETE 文の中で種目と記録が一緒に消えるため
  exercise_id BIGINT   NOT NULL REFERENCES exercises (id) ON DELETE NO ACTION,
  sort_order  SMALLINT NOT NULL CHECK (sort_order BETWEEN 1 AND 20),
  UNIQUE (workout_id, sort_order)
);

-- 種目での絞り込み・種目ごとの推移・自己ベスト・自分専用の種目を消せるかの確認
CREATE INDEX ix_workout_exercises_exercise ON workout_exercises (exercise_id, workout_id);

CREATE TABLE workout_sets (
  id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workout_exercise_id BIGINT       NOT NULL REFERENCES workout_exercises (id) ON DELETE CASCADE,
  sort_order          SMALLINT     NOT NULL CHECK (sort_order BETWEEN 1 AND 20),
  -- 下限 0.226 は、0.5lb（0.227kg）も入る値。細かい範囲と刻みはアプリで確かめる
  weight_kg           NUMERIC(6,3) CHECK (weight_kg BETWEEN 0.226 AND 500),
  reps                INTEGER      CHECK (reps BETWEEN 0 AND 9999),
  duration_sec        INTEGER      CHECK (duration_sec BETWEEN 1 AND 86400),
  distance_km         NUMERIC(6,3) CHECK (distance_km BETWEEN 0.01 AND 500),
  UNIQUE (workout_exercise_id, sort_order),
  -- どの記録方式の形にも当てはまらない行を入れない
  CHECK (
       (weight_kg IS NOT NULL AND reps IS NOT NULL AND duration_sec IS NULL AND distance_km IS NULL)
    OR (weight_kg IS NULL AND reps IS NOT NULL AND duration_sec IS NULL AND distance_km IS NULL)
    OR (weight_kg IS NULL AND reps IS NULL AND duration_sec IS NOT NULL)
  )
);
