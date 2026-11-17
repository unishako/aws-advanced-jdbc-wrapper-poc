#!/bin/bash
# フェイルオーバーを疑似的に発生させる。
#   1. 旧 Writer を停止する (障害の模倣)
#        先に停止することで、停止時の最後の WAL が、昇格前にすべての Reader へ届く。
#        (昇格してから停止すると、Reader の一部だけが先に進み、新しい Writer に追従できなくなる)
#   2. reader1 を Writer に昇格する (pg_promote)
#   3. クラスターエンドポイントの alias を、昇格した reader1 に付け替える
#   4. 残りの Reader (reader2) を、新しい Writer に付け替える
#        - 新しい Writer に、その Reader 用のレプリケーションスロットを作る
#        - その Reader の接続先 (primary_conninfo) を、新しい Writer に変更する
#
# 元に戻すときは scripts/reset-db.sh で DB を作り直す。
set -euo pipefail

CLUSTER_ID="abcdefghijkl.ap-northeast-1.rds.amazonaws.com"
NETWORK="aurora-poc"
WRITER="aurora-poc-writer"
NEW_WRITER="aurora-poc-reader1"            # 昇格するコンテナ
NEW_WRITER_HOST="postgres-reader1"         # 昇格するコンテナの compose サービス名 (ネットワーク内で名前解決できる)
OTHER_READERS=("reader2")                  # 付け替える残りの Reader (コンテナ名は aurora-poc-<名前>、スロット名は <名前>_slot)
DB_USER="${POSTGRES_USER:-postgres}"
DB_NAME="${POSTGRES_DB:-app}"
REPLICATION_PASSWORD="${REPLICATION_PASSWORD:-replicator}"

echo "[1/4] 旧 Writer (${WRITER}) を停止します"
docker stop "$WRITER"

echo "[2/4] ${NEW_WRITER} を Writer に昇格します"
docker exec "$NEW_WRITER" psql -U "$DB_USER" -d "$DB_NAME" -At -c "select pg_promote(wait => true);"

echo "[3/4] クラスターエンドポイントの alias を ${NEW_WRITER} に付け替えます"
# ネットワークから切断して再接続すると、compose が付けたサービス名の alias が失われるため、あらためて指定する。
# リーダーエンドポイント (poc.cluster-ro-…) は、残りの Reader だけに残す (Reader がいれば Writer は含まれない)
docker network disconnect "$NETWORK" "$NEW_WRITER"
docker network connect "$NETWORK" "$NEW_WRITER" \
  --alias "$NEW_WRITER_HOST" \
  --alias "reader1.${CLUSTER_ID}" \
  --alias "poc.cluster-${CLUSTER_ID}"

echo "[4/4] 残りの Reader を、新しい Writer (${NEW_WRITER}) に付け替えます"
for name in "${OTHER_READERS[@]}"; do
  echo "  - ${name}: 新しい Writer にスロット ${name}_slot を作成"
  docker exec "$NEW_WRITER" psql -U "$DB_USER" -d "$DB_NAME" -At \
    -c "select pg_create_physical_replication_slot('${name}_slot', true);"
  echo "  - ${name}: 接続先を ${NEW_WRITER_HOST} に変更"
  docker exec "aurora-poc-${name}" psql -U "$DB_USER" -d "$DB_NAME" -At \
    -c "alter system set primary_conninfo = 'host=${NEW_WRITER_HOST} port=5432 user=replicator password=${REPLICATION_PASSWORD}'" \
    -c "select pg_reload_conf();"
done

echo "完了: wrapper の failover2 が新しい Writer へ再接続するかをアプリで確認してください"
echo "  curl localhost:8080/api/diag/connections"
echo "  curl localhost:8080/api/diag/topology"
