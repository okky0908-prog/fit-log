-- メールアドレスの確認用のトークン（docs/database.md「email_verification_tokens」）。
-- 1人に1行だけ（UNIQUE(user_id)）。新しいリンクを送ると行を上書きするので、前のリンクは使えなくなる
CREATE TABLE email_verification_tokens (
  id         BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id    BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  -- 確認するメールアドレス。登録のときは今のアドレス、変更のとき（F-06。段階 5）は新しいアドレス
  email      VARCHAR(254) NOT NULL CHECK (email = LOWER(email)),
  -- トークンの SHA-256 のハッシュ。トークンそのものは保存しない
  token_hash CHAR(64)     NOT NULL,
  expires_at TIMESTAMPTZ  NOT NULL,
  created_at TIMESTAMPTZ  NOT NULL,
  CONSTRAINT uq_email_verification_tokens_user_id UNIQUE (user_id),
  CONSTRAINT uq_email_verification_tokens_token_hash UNIQUE (token_hash)
);

-- メールの送信の記録（docs/database.md「mail_send_logs」）。同じアドレスに1時間で5通までの上限を数える。
-- 登録されていないアドレスにも送るので、users を参照しない
CREATE TABLE mail_send_logs (
  id        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  email     VARCHAR(254) NOT NULL,
  mail_type VARCHAR(20)  NOT NULL CHECK (mail_type IN ('verify_email', 'password_reset')),
  sent_at   TIMESTAMPTZ  NOT NULL
);

-- 送信の上限の確認（そのアドレスの直近1時間の数）
CREATE INDEX idx_mail_send_logs_email_sent_at ON mail_send_logs (email, sent_at);
