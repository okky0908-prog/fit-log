-- 通知（docs/database.md「notifications」）。
-- コメント・目標の表はまだないので、comment_id・goal_id・goal_achievement_id の列は、それぞれの表を作るマイグレーションで足す。
-- そのときに、種類ごとの列の決まり（ck_notifications_columns）も作り直す
CREATE TABLE notifications (
  id           BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  recipient_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  -- 操作した人。目標の達成は NULL
  actor_id     BIGINT      REFERENCES users (id) ON DELETE CASCADE,
  type         VARCHAR(20) NOT NULL CHECK (type IN (
                 'like', 'comment', 'reply', 'follow', 'follow_request', 'follow_accepted',
                 'goal_achieved', 'goal_cheer')),
  workout_id   BIGINT      REFERENCES workouts (id) ON DELETE CASCADE,
  created_at   TIMESTAMPTZ NOT NULL,
  -- 既読にした日時。NULL なら未読
  read_at      TIMESTAMPTZ,
  -- 自分の操作で自分に通知を作らない（F-110）
  CONSTRAINT ck_notifications_not_self CHECK (actor_id IS NULL OR actor_id <> recipient_id),
  -- 種類ごとに、値を入れる列（今ある列の分だけ）
  CONSTRAINT ck_notifications_columns CHECK (
    CASE type
      WHEN 'like'            THEN actor_id IS NOT NULL AND workout_id IS NOT NULL
      WHEN 'comment'         THEN actor_id IS NOT NULL AND workout_id IS NOT NULL
      WHEN 'reply'           THEN actor_id IS NOT NULL AND workout_id IS NOT NULL
      WHEN 'follow'          THEN actor_id IS NOT NULL AND workout_id IS NULL
      WHEN 'follow_request'  THEN actor_id IS NOT NULL AND workout_id IS NULL
      WHEN 'follow_accepted' THEN actor_id IS NOT NULL AND workout_id IS NULL
      WHEN 'goal_achieved'   THEN actor_id IS NULL AND workout_id IS NULL
      WHEN 'goal_cheer'      THEN actor_id IS NOT NULL AND workout_id IS NULL
    END
  )
);

-- 通知一覧（新しい順）
CREATE INDEX ix_notifications_recipient_created
  ON notifications (recipient_id, created_at DESC, id DESC);
-- 未読数。未読の行だけを持つので小さい
CREATE INDEX ix_notifications_unread ON notifications (recipient_id) WHERE read_at IS NULL;
-- 取り消したときの削除と、退会・投稿の削除の CASCADE
CREATE INDEX ix_notifications_actor ON notifications (actor_id);
CREATE INDEX ix_notifications_workout ON notifications (workout_id);
