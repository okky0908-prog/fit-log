-- ナイストレーニング（docs/database.md「likes」）
CREATE TABLE likes (
  id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workout_id BIGINT      NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
  -- 付けた人
  user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL,
  -- 同じ人は1回だけ。連打されても INSERT … ON CONFLICT DO NOTHING で1件にする。数と「付けたか」もこの索引で数える
  UNIQUE (workout_id, user_id)
);

-- 退会のときの CASCADE
CREATE INDEX ix_likes_user ON likes (user_id);
