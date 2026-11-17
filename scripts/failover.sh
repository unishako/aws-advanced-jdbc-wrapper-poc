#!/bin/bash
# フェイルオーバーを疑似的に発生させる。
#   1. Reader を Writer に昇格する (pg_promote)
#   2. 旧 Writer を停止する (障害の模倣)
#   3. クラスターエンドポイントの alias を、昇格した Reader に付け替える
#
# 元に戻すときは scripts/reset-db.sh で DB を作り直す。
set -euo pipefail

CLUSTER_ID="abcdefghijkl.ap-northeast-1.rds.amazonaws.com"
NETWORK="aurora-poc"
WRITER="aurora-poc-writer"
READER="aurora-poc-reader1"
DB_USER="${POSTGRES_USER:-postgres}"
DB_NAME="${POSTGRES_DB:-app}"

echo "[1/3] ${READER} を Writer に昇格します"
docker exec "$READER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "select pg_promote(wait => true);"

echo "[2/3] 旧 Writer (${WRITER}) を停止します"
docker stop "$WRITER"

echo "[3/3] クラスターエンドポイントの alias を ${READER} に付け替えます"
docker network disconnect "$NETWORK" "$READER"
docker network connect "$NETWORK" "$READER" \
  --alias "reader1.${CLUSTER_ID}" \
  --alias "poc.cluster-${CLUSTER_ID}" \
  --alias "poc.cluster-ro-${CLUSTER_ID}"

echo "完了: wrapper の failover2 が新しい Writer へ再接続するかをアプリで確認してください"
echo "  curl localhost:8080/api/diag/connections"
