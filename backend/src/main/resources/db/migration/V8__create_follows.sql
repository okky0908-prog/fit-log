-- フォロー・フォローのリクエスト（docs/database.md「follows」）
CREATE TABLE follows (
  id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  follower_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  followee_id BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  -- pending：リクエスト中、accepted：承認済み
  status      VARCHAR(10) NOT NULL CHECK (status IN ('pending', 'accepted')),
  created_at  TIMESTAMPTZ NOT NULL,
  -- 承認された日時。鍵アカウントでない人へのフォローは created_at と同じ
  accepted_at TIMESTAMPTZ,
  -- リクエスト中も1件と数える（F-50）
  UNIQUE (follower_id, followee_id),
  -- 自分自身はフォローできない
  CHECK (follower_id <> followee_id),
  CHECK ((status = 'accepted') = (accepted_at IS NOT NULL))
);

-- 閲覧の条件（承認済みのフォロワーか）・フォロー中一覧（新しい順）
CREATE INDEX ix_follows_follower ON follows (follower_id, accepted_at DESC, id DESC);
-- フォロワー一覧（新しい順）・フォローのリクエストの数と一覧
CREATE INDEX ix_follows_followee ON follows (followee_id, status, accepted_at DESC, id DESC);
