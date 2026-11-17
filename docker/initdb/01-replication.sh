#!/bin/bash
# Writer の初回起動時のみ実行される初期化スクリプト。
# Reader(スタンバイ)がレプリケーションできるよう、専用ユーザー・スロット・pg_hba を設定する。
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD}';
  -- 第2引数 true: スロット作成時から WAL を確保する (複数の Reader が同時にベースバックアップを取っても、必要な WAL が削除されない)
  SELECT pg_create_physical_replication_slot('reader1_slot', true);
  SELECT pg_create_physical_replication_slot('reader2_slot', true);
EOSQL

# レプリケーション接続の許可 (docker ネットワーク内のみ)
echo "host replication replicator all scram-sha-256" >> "$PGDATA/pg_hba.conf"
