-- 記録の画像（docs/database.md「workout_images」）。S3 のキーだけを保存し、URL は閲覧を許可したあとに署名付き URL を作る
CREATE TABLE workout_images (
  id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workout_id BIGINT       NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
  image_key  VARCHAR(255) NOT NULL,
  sort_order SMALLINT     NOT NULL CHECK (sort_order BETWEEN 1 AND 4),
  created_at TIMESTAMPTZ  NOT NULL,
  UNIQUE (workout_id, sort_order)
);
