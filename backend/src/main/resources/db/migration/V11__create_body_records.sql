-- 体重の記録（docs/database.md「body_records」）。本人だけが見られる
CREATE TABLE body_records (
  id           BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
  -- 未来の日付は不可（アプリで確かめる。「今日」は日本時間でアプリが決める）
  recorded_on  DATE         NOT NULL,
  -- kg で小数第3位まで。44.0lb ＝ 19.958kg なので、下限は lb で入力したときに合わせる。細かい範囲と刻みはアプリで確かめる
  weight_kg    NUMERIC(6,3) NOT NULL CHECK (weight_kg BETWEEN 19.95 AND 300),
  body_fat_pct NUMERIC(3,1) CHECK (body_fat_pct BETWEEN 1.0 AND 75.0),
  created_at   TIMESTAMPTZ  NOT NULL,
  updated_at   TIMESTAMPTZ  NOT NULL,
  -- 1日1件。同じ日に記録したら INSERT … ON CONFLICT DO UPDATE で上書きする。一覧・推移・実施日以前で一番近い体重にもこの索引を使う
  UNIQUE (user_id, recorded_on)
);
