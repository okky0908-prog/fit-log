-- ユーザー（docs/database.md「users」）と、日本語の部分一致検索の拡張 pg_bigm
CREATE EXTENSION IF NOT EXISTS pg_bigm;

CREATE TABLE users (
  id                BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username          VARCHAR(15)  NOT NULL CHECK (username ~ '^[A-Za-z0-9_]{4,15}$'),
  display_name      VARCHAR(50)  NOT NULL CHECK (LENGTH(display_name) >= 1),
  email             VARCHAR(254) NOT NULL UNIQUE CHECK (email = LOWER(email)),
  email_verified_at TIMESTAMPTZ,
  password_hash     VARCHAR(255) NOT NULL,
  bio               VARCHAR(160),
  icon_key          VARCHAR(255),
  is_private        BOOLEAN      NOT NULL DEFAULT false,
  weight_unit       VARCHAR(2)   NOT NULL DEFAULT 'kg' CHECK (weight_unit IN ('kg', 'lb')),
  distance_unit     VARCHAR(2)   NOT NULL DEFAULT 'km' CHECK (distance_unit IN ('km', 'mi')),
  created_at        TIMESTAMPTZ  NOT NULL,
  updated_at        TIMESTAMPTZ  NOT NULL
);

-- 大文字・小文字を区別せずにユーザー名の重複を防ぐ
CREATE UNIQUE INDEX uq_users_username_lower ON users (LOWER(username));
-- 新しいユーザー一覧（F-71）
CREATE INDEX idx_users_created_at ON users (created_at DESC, id DESC);
-- ユーザー検索の部分一致（F-70）
CREATE INDEX idx_users_username_bigm ON users USING gin (LOWER(username) gin_bigm_ops);
CREATE INDEX idx_users_display_name_bigm ON users USING gin (LOWER(display_name) gin_bigm_ops);
