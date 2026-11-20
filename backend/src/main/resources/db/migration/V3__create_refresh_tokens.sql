-- リフレッシュトークン（docs/database.md「refresh_tokens」。前回の SNS アプリと同じ）。
-- トークンそのものは保存せず、SHA-256 のハッシュだけを保存する（DB が漏れても、トークンとして使えないように）
CREATE TABLE refresh_tokens (
  id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  token_hash CHAR(64)    NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  -- 無効にした日時（再発行・ログアウト・使い回しの検知）。NULL なら有効
  revoked_at TIMESTAMPTZ,
  -- 再発行で新しいトークンに交換した日時。交換から10秒以内の再利用を「同時のリクエスト」として許す判定に使う
  -- （revoked_at だけでは、ログアウト・使い回しの検知による無効化と区別できないため）
  rotated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash)
);

-- そのユーザーのトークンをまとめて無効にするとき（使い回しの検知）と、ユーザーの削除の CASCADE に使う
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
