-- コメント・返信（docs/database.md「comments」）。返信は1段だけ（返信元は返信でないコメント）で、それはアプリで確かめる
CREATE TABLE comments (
  id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  workout_id       BIGINT       NOT NULL REFERENCES workouts (id) ON DELETE CASCADE,
  -- 書いた人
  user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  -- 返信元のコメント。返信でなければ NULL。返信元を消すと返信も消える（F-32）
  parent_id        BIGINT       REFERENCES comments (id) ON DELETE CASCADE,
  -- 返信先のユーザー（@xxx の表示と、返信の通知の受け取り手）。退会したら返信は残して NULL にする
  reply_to_user_id BIGINT       REFERENCES users (id) ON DELETE SET NULL,
  body             VARCHAR(140) NOT NULL CHECK (LENGTH(body) >= 1),
  created_at       TIMESTAMPTZ  NOT NULL,
  -- 返信でないコメントには返信先を持たせない
  CHECK (parent_id IS NOT NULL OR reply_to_user_id IS NULL)
);

-- 返信元のコメントを古い順に（F-31）
CREATE INDEX ix_comments_workout_created
  ON comments (workout_id, created_at, id) WHERE parent_id IS NULL;
-- 返信を古い順に・返信の数・返信元を消すときの CASCADE
CREATE INDEX ix_comments_parent_created ON comments (parent_id, created_at, id);
-- コメント数（返信を含む）・投稿を消すときの CASCADE
CREATE INDEX ix_comments_workout ON comments (workout_id);
-- 退会のときの CASCADE・SET NULL
CREATE INDEX ix_comments_user ON comments (user_id);
CREATE INDEX ix_comments_reply_to_user ON comments (reply_to_user_id);

-- 通知に、対象のコメントの列を足す（コメント・返信のときは、書かれたコメントそのもの）。コメントを消すと通知も消える
ALTER TABLE notifications
  ADD COLUMN comment_id BIGINT REFERENCES comments (id) ON DELETE CASCADE;
CREATE INDEX ix_notifications_comment ON notifications (comment_id);

-- 種類ごとに値を入れる列の決まりを、comment_id を含めて作り直す（docs/database.md「notifications」の表）
ALTER TABLE notifications DROP CONSTRAINT ck_notifications_columns;
ALTER TABLE notifications ADD CONSTRAINT ck_notifications_columns CHECK (
  CASE type
    WHEN 'like'            THEN actor_id IS NOT NULL AND workout_id IS NOT NULL AND comment_id IS NULL
    WHEN 'comment'         THEN actor_id IS NOT NULL AND workout_id IS NOT NULL AND comment_id IS NOT NULL
    WHEN 'reply'           THEN actor_id IS NOT NULL AND workout_id IS NOT NULL AND comment_id IS NOT NULL
    WHEN 'follow'          THEN actor_id IS NOT NULL AND workout_id IS NULL AND comment_id IS NULL
    WHEN 'follow_request'  THEN actor_id IS NOT NULL AND workout_id IS NULL AND comment_id IS NULL
    WHEN 'follow_accepted' THEN actor_id IS NOT NULL AND workout_id IS NULL AND comment_id IS NULL
    WHEN 'goal_achieved'   THEN actor_id IS NULL AND workout_id IS NULL AND comment_id IS NULL
    WHEN 'goal_cheer'      THEN actor_id IS NOT NULL AND workout_id IS NULL AND comment_id IS NULL
  END
);
