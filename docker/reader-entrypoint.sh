#!/bin/bash
# Reader(スタンバイ)用エントリポイント。
# データディレクトリが空なら Writer から pg_basebackup でコピーし、スタンバイとして起動する。
set -e

if [ ! -s "$PGDATA/PG_VERSION" ]; then
  echo "Reader: データディレクトリが空のため、Writer からベースバックアップを取得します"
  mkdir -p "$PGDATA"
  chown -R postgres:postgres "$PGDATA"
  chmod 700 "$PGDATA"

  gosu postgres env PGPASSWORD="$REPLICATION_PASSWORD" \
    pg_basebackup \
      --host="$WRITER_HOST" \
      --username=replicator \
      --pgdata="$PGDATA" \
      --wal-method=stream \
      --slot=reader_slot \
      --write-recovery-conf \
      --progress
fi

exec docker-entrypoint.sh "$@"
