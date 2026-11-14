#!/bin/bash
# Writer の初回起動時のみ実行される初期化スクリプト。
# Reader(スタンバイ)がレプリケーションできるよう、専用ユーザー・スロット・pg_hba を設定する。
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
  CREATE ROLE replicator WITH REPLICATION LOGIN PASSWORD '${REPLICATION_PASSWORD}';
  SELECT pg_create_physical_replication_slot('reader_slot');
EOSQL

# レプリケーション接続の許可 (docker ネットワーク内のみ)
echo "host replication replicator all scram-sha-256" >> "$PGDATA/pg_hba.conf"
