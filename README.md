# aws-advanced-jdbc-wrapper-poc

[AWS Advanced JDBC Wrapper](https://github.com/aws/aws-advanced-jdbc-wrapper) の挙動(読み書きの振り分け・コネクションプール・フェイルオーバー)を、
ローカルの Docker 上で検証するための PoC です。Aurora PostgreSQL の Writer 1台と Reader 2台(`reader1`、`reader2`)を、PostgreSQL 18 で模倣しています。

「Aurora を用意せずに、wrapper の設定を試したい」「Spring Boot + Doma で、Writer / Reader の振り分けを確認したい」といった用途を想定しています。

> **ご注意**
> - 非公式のサンプルであり、Amazon Web Services 社とは関係ありません。AWS、Amazon Aurora は Amazon.com, Inc. またはその関連会社の商標です。
> - 検証用の PoC です。本番環境での利用は想定していません。Aurora の挙動を完全に再現するものでもありません(「補足」を参照)。

- Java 25 (LTS) / Spring Boot 4.1.2 / Doma 3.15.0 / AWS Advanced JDBC Wrapper 4.6.0 / PostgreSQL 18
- 設定はすべて `src/main/resources/application.properties`

## 構成

```
docker-compose.yml        Writer / Reader / アプリ / テスト
docker/Dockerfile.postgres  PostgreSQL 18 + 日本語ロケール(ログも日本語)
docker/initdb/            Writer の初回起動時に自動実行: レプリケーション設定 → Aurora関数 → テーブル → 初期データ
docker/reader-entrypoint.sh  Reader の初回起動時に pg_basebackup でスタンバイを構築
scripts/failover.sh       フェイルオーバーの疑似発生
scripts/reset-db.sh       DB を初期状態に作り直す
src/                      Spring Boot アプリ (Doma 3 / 業務API / 診断API)
```

### Aurora の模倣方法

素の PostgreSQL にはない、wrapper が Aurora 判定とトポロジー取得に使う要素を再現しています。

| Aurora | ローカルでの再現 |
|---|---|
| クラスター / リーダー / インスタンスエンドポイント | compose ネットワークの alias で RDS 形式のホスト名を提供 (`poc.cluster-…` が Writer、`poc.cluster-ro-…` が Reader。reader1 と reader2 が同じ別名を共有し、名前解決で両方のアドレスが返る) |
| `aurora_replica_status()` / `aurora_db_instance_identifier()` | 同名の SQL 関数を `docker/initdb/02-aurora-emulation.sql` で作成 |
| Writer / Reader | PostgreSQL のストリーミングレプリケーション |

## 前提条件

- Docker (Docker Compose v2 が使えること)
- ポート 5432 / 5433 / 8080 が空いていること(変更する場合は [`.env.example`](.env.example) を参照)
- ローカルの Java は不要です(アプリのビルドと実行は Docker 内で行います)。IDE で開発する場合のみ Java 25 が必要です。

## 起動

```sh
git clone <このリポジトリの URL>
cd <クローンしたディレクトリ>
docker compose up -d --build
```

初回起動時に、テーブルが自動作成され、初期データ(顧客10,000件・商品1,000件・注文50,000件)が投入されます。
データは `docker/postgres/` に保存されます。
以前の版からの更新などで、すでに `docker/postgres/` にデータがある場合は、`bash scripts/reset-db.sh` で作り直してください(Aurora の関数、レプリケーションのスロット、インスタンス名は、初回起動時にしか反映されません)。

起動後の確認例:

```sh
curl localhost:8080/api/diag/connections   # 読み取りは reader1 / reader2、書き込みは writer に接続していること
curl 'localhost:8080/api/products?limit=3' # 商品の一覧 (Reader から取得)
```

| 用途 | URL |
|---|---|
| アプリ | http://localhost:8080 |
| Writer (psql 等) | localhost:5432 |
| Reader1 (psql 等) | localhost:5433 |
| Reader2 (psql 等) | localhost:5434 |

接続情報は環境変数 `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` で変更できます(既定: postgres / postgres / app)。

## API

| メソッド | パス | 実行先 |
|---|---|---|
| GET | `/api/products?category=&limit=&offset=` / `/api/products/{id}` | Reader |
| GET | `/api/customers` / `/api/customers/{id}` / `/api/customers/{id}/orders` | Reader |
| GET | `/api/orders/{id}` / `/api/orders/summary` (カテゴリ別売上) | Reader |
| POST | `/api/products` / `/api/products/{id}/stock` / `/api/customers` / `/api/orders` | Writer |
| GET | `/api/diag/connections` 読み取りTx・書き込みTxの接続先 | Reader / Writer |
| GET | `/api/diag/pools` Writer / Reader それぞれへの接続数 | Reader / Writer |
| GET | `/api/diag/topology` `aurora_replica_status()` の内容 | Writer |
| POST | `/api/diag/write-then-read` 書き込み直後に Reader から読み取る(レプリカラグの確認) | Writer → Reader |

`@Transactional(readOnly = true)` の処理は Reader に、それ以外は Writer に向かいます。

## 設定 (application.properties)

wrapper のオプションとプールの本数は、独立した設定項目です。

| 項目 | 内容 |
|---|---|
| `poc.wrapper.plugins` | `initialConnection,readWriteSplitting,failover2,efm2` |
| `poc.wrapper.failover.*` | フェイルオーバーのモード・タイムアウト・トポロジー更新間隔 |
| `poc.wrapper.reader-host-selector-strategy` | Reader が複数あるときの選択方式(`random`(既定)/ `roundRobin` / `leastConnections`(内部プールが必須)など) |
| `poc.wrapper.initial-connection.open-connection-retry-timeout-ms` | 接続を開くときのリトライの最大時間。停止した Reader が選ばれたとき、この時間だけ待ってから別の Reader に切り替わる(既定 30000) |
| `poc.pool.maximum-size` | 1インスタンスあたりの最大接続数 (Writer / Reader それぞれに適用) |
| `poc.pool.minimum-idle` | 1インスタンスあたりの最小アイドル接続数 |
| `poc.pool.auto-commit` | 自動コミット(既定の設定は `false`)。コミットと、ロールバックは `@Transactional` が制御します。`@Transactional` の外で実行した更新は、コミットされません |
| `poc.pool.transaction-isolation` | トランザクション分離レベル(既定の設定は `TRANSACTION_READ_COMMITTED`) |
| `poc.pool.read-only` | プールが作る接続を読み取り専用にするか(既定の設定は `false`、HikariCP の既定値と同じ)。`true` にすると Writer の更新ができなくなります。読み書きの振り分けは、この設定ではなく `@Transactional(readOnly = true)` で行います |

`application.properties` では、各設定に `[必須]` `[環境]` `[調整]` `[変数]` のラベルを付けています(意味は、ファイルの先頭の凡例を参照)。

DataSource は Spring Boot 標準の `DriverManagerDataSource`(プールなし)で、プールは wrapper の内部プール
(`connectionPoolType=hikari`)が担当します。設定値は `spring.datasource.url` のパラメータとして wrapper に渡されます。
wrapper やプールのための自前の Java 実装はありません(すべて設定です)。
内部プールは、そのインスタンスへ最初に接続したときに作られる(遅延作成)ため、Reader のプールは最初の読み取りで作られます。
(実測: 起動直後は Writer に3本・Reader に0本、最初の読み取りの後は Reader に2本。Writer は起動中の処理で接続されていた。)
起動時に Reader のプールも確保したい場合は、起動時に読み取り専用の接続を1回開く処理が必要です(設定だけでは指定できません)。

### 検証で分かったこと

- **クラスターエンドポイントへの接続は内部プールの対象外**です(wrapper の公式ドキュメント)。
  そのままだと Writer のプールが作られないため、`initialConnection` プラグインで、初期接続を Writer のインスタンスのエンドポイントに置き換えています。
- 内部プールは**インスタンスごと**に作られ、`cp-` で始まるパラメータ(HikariCP の設定)が全インスタンス共通で適用されます。Writer と Reader でサイズを分けることはできません。
- Writer の接続数は、プール上限+1 になります(wrapper の内部監視用の接続)。
- 昇格後のシーケンスは、先読み分(最大32)だけ進むため、ID に飛びが出ます(PostgreSQL の通常の仕様)。
- Reader の `replica_lag_in_msec` は、書き込みがない間は「最終反映からの経過時間」になるので、大きな値になります。実際の遅延の確認には `/api/diag/write-then-read` を使ってください。

## Reader のスケールアウトの検証

Reader は2台(`reader1`、`reader2`)です。Reader を増やす場合は、`docker-compose.yml` に `postgres-reader3` などを追加し、
`01-replication.sh` にスロットを足し、`poc.members` に名前を追加します(`02-aurora-emulation.sql` の関数がメンバー一覧からトポロジーを返します)。

作者の環境(Docker Desktop on macOS)での確認結果:

| 確認 | 結果 |
|---|---|
| トポロジーの検出 | `writer` / `reader1` / `reader2` の3台を検出。リーダーエンドポイントは両方のアドレスを返す |
| `random`(既定)60回 | reader1: 28、reader2: 32 |
| `roundRobin` 60回 | reader1: 30、reader2: 30(交互) |
| `leastConnections` 200回・並列20 | reader1: 98、reader2: 102 |
| プール | アプリごと・Reader ごとに作られる(各 Reader に最小アイドル数の接続)。**Reader が増えると、アプリ1台あたりの接続数は、Reader の台数 × 上限になる** |
| Reader を1台停止(約10秒後) | 停止した Reader が、Writer から見たトポロジーから外れ、以降の読み取りはすべて残りの Reader に接続(20回とも最大0.02秒)。再開すると、トポロジーと分散に復帰する |
| Reader の停止直後(トポロジーから外れる前) | 停止した Reader が選ばれると、`open-connection-retry-timeout-ms` の時間(既定30秒、5000なら約5秒)待ってから、残りの Reader に切り替わる。エラーにはならない |

選択方式は、設定ファイルを変えずに、環境変数で一時的に切り替えて試せます(例: 一時的なアプリのコンテナを起動)。

```sh
docker run -d --rm --name poc-app-rr --network aurora-poc -p 127.0.0.1:8081:8080 \
  -e POC_WRAPPER_READER_HOST_SELECTOR_STRATEGY=roundRobin \
  $(docker inspect -f '{{.Config.Image}}' aws-advanced-jdbc-wrapper-poc-app)
curl localhost:8081/api/diag/connections
docker stop poc-app-rr
```

注意:

- 停止した Reader がトポロジーから外れるのは、`02-aurora-emulation.sql` が、Writer から呼ばれたときに、レプリケーションが切れている(スロットが `active` でない)Reader を結果から除くためです。本物の Aurora で、更新が止まった Reader が時間差で外れるのを模倣しています。Reader から呼ばれた場合は、他のインスタンスの死活が分からないため、全メンバーを返します。接続が突然切れた場合など、Writer が切断に気づくまでの間は、トポロジーに残る場合があります(未検証)。
- トポロジーのメンバー一覧(`poc.members`)は、起動時の設定です。フェイルオーバー後も、Reader から呼ばれた関数は、先頭の `writer` を Writer として扱います。

## テスト

DB(Writer / Reader)への接続が必要なため、compose のネットワーク内で実行します。

```sh
docker compose --profile test run --rm --build app-test
```

`ConnectionPoolStartupTest`:

- 最初のアクセスで Writer / Reader の両方にコネクションプールが作られる
- Writer / Reader の接続がプールされ、再利用される(連続10回の接続で、バックエンドのプロセスが使い回される)
- 読み取り専用トランザクションは Reader に、書き込みトランザクションは Writer に接続する

`ReaderScaleOutTest`:

- Writer から見たトポロジーに、`writer`、`reader1`、`reader2` の3台が含まれる
- 読み取りが、2台の Reader の両方に分散される(`random` で30回。片方が一度も選ばれない確率は約20億分の1)

環境変数 `RUN_DB_TESTS=true` のときだけ実行されます(`./gradlew test` を単体で実行した場合はスキップされます)。

## フェイルオーバーの確認

```sh
bash scripts/failover.sh               # 旧 Writer を停止 → reader1 を昇格 → エンドポイントを付け替え → reader2 を新しい Writer に付け替え
curl localhost:8080/api/diag/connections
bash scripts/reset-db.sh               # 元に戻す(docker/postgres/ のデータは削除される)
```

作者の環境(Docker Desktop on macOS)での確認結果:

- Reader が1台の構成(旧版のスクリプト): 直後の最初のリクエストは約15秒かかりましたが、エラーにならず成功し(wrapper の `failover2` が再接続)、以降は昇格したインスタンスで読み書きできました。
- Reader が2台の構成: 昇格した `reader1` が `MASTER` として、`reader2` が Reader として、トポロジーに載り、旧 Writer は外れました。読み取りは `reader2`、書き込みは `reader1` に向かい、新しい Writer への書き込みが `reader2` にも届きました。

所要時間は環境や設定(`poc.wrapper.failover.*`)によって変わります。旧 Writer を Reader として復帰させる処理は含めていません(`reset-db.sh` で作り直します)。

**旧 Writer を先に停止する理由**: 先に昇格してから旧 Writer を停止すると、旧 Writer が停止時に書く最後の WAL を、まだ接続していた `reader2` だけが受け取り、昇格済みの `reader1` は受け取れません。その結果、`reader2` が新しい Writer より先に進み、追従できなくなります(本物の Aurora は、共有ストレージなので起きません)。
また、昇格した `reader1` をネットワークに付け直すと、compose が付けたサービス名の別名が失われるため、別名を指定し直しています。

## 参考

- [AWS Advanced JDBC Wrapper(GitHub)](https://github.com/aws/aws-advanced-jdbc-wrapper)
- [Read/Write Splitting プラグイン(内部コネクションプールを含む)](https://github.com/aws/aws-advanced-jdbc-wrapper/blob/main/docs/using-the-jdbc-driver/using-plugins/UsingTheReadWriteSplittingPlugin.md)
- [Failover v2 プラグイン](https://github.com/aws/aws-advanced-jdbc-wrapper/blob/main/docs/using-the-jdbc-driver/using-plugins/UsingTheFailover2Plugin.md)
- [Configure AWS Advanced JDBC Wrapper connection pooling with the assistant(AWS Database Blog)](https://aws.amazon.com/blogs/database/configure-aws-advanced-jdbc-wrapper-connection-pooling-with-the-assistant/)

## ライセンス

[MIT License](LICENSE) です。
使用しているライブラリ(AWS Advanced JDBC Wrapper、Doma、Spring Boot、HikariCP など)は、主に Apache License 2.0 です。
ビルドした jar や Docker イメージを再配布する場合は、各ライブラリのライセンス表示(Logback の LGPL-2.1 / EPL-2.0 など)を同梱してください。

## 補足

- ディレクトリ名は任意です(`docker-compose.yml` の `name:` と Gradle の `rootProject.name` を明示しているため、名前を変えても動作します)。
- 公開リポジトリのため、既定のパスワード(`postgres` など)はローカル検証用です。ポートは `127.0.0.1` のみで待ち受けます。実環境では使わないでください。
- 偽の RDS ホスト名は compose のネットワーク内でしか解決できないため、アプリも compose 内で動かします。
- Aurora 固有の挙動(共有ストレージによる低いレプリカラグ、DNS によるエンドポイントの切り替え)は再現できません。
