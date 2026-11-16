# pg_bigm（日本語の部分一致検索の索引）を入れた PostgreSQL 17（docs/tech-stack.md 3.2）。
# 公式のイメージにも、PostgreSQL の公式の apt リポジトリにも pg_bigm がないため、ソースからビルドする。
# Testcontainers（テスト・jOOQ のコード生成）と docker-compose（手元の開発）の両方でこのイメージを使う

# pg_bigm の版。上げるときは、GitHub のリリース（https://github.com/pgbigm/pg_bigm/releases）で
# PostgreSQL 17 に対応していることを確かめ、ダウンロードしたファイルの SHA-256 を書き換える
ARG PG_BIGM_VERSION=1.2-20250903
ARG PG_BIGM_SHA256=4d4fb48161e3f9e99207851299379c83b3e5d1d3c1a07c35f6a3153d577b3142

# 1段目：ビルドに使う道具を入れて pg_bigm をビルドし、/out に置く
# （libicu-dev は、PostgreSQL のヘッダーが ICU のヘッダーを読み込むため）
FROM postgres:17 AS build
ARG PG_BIGM_VERSION
ARG PG_BIGM_SHA256
RUN apt-get update \
    && apt-get install -y --no-install-recommends build-essential ca-certificates curl libicu-dev "postgresql-server-dev-${PG_MAJOR}" \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /src
RUN curl -fsSL -o pg_bigm.tar.gz "https://github.com/pgbigm/pg_bigm/archive/refs/tags/v${PG_BIGM_VERSION}.tar.gz" \
    && echo "${PG_BIGM_SHA256}  pg_bigm.tar.gz" | sha256sum -c - \
    && tar xzf pg_bigm.tar.gz --strip-components=1 \
    && make USE_PGXS=1 \
    && make USE_PGXS=1 install DESTDIR=/out

# 2段目：ビルドした拡張のファイルだけを、公式のイメージに足す（ビルドの道具は入れない）
FROM postgres:17
COPY --from=build /out/ /
