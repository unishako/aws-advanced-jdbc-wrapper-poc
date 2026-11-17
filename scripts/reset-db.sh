#!/bin/bash
# DB (Writer/Reader) を初期状態に作り直す。フェイルオーバー後の復旧や、スキーマ・シード変更の反映に使う。
# docker/postgres/ 配下のデータはすべて削除される。
set -euo pipefail

cd "$(dirname "$0")/.."

docker compose down
rm -rf docker/postgres/writer docker/postgres/reader1 docker/postgres/reader2
docker compose up -d --build
