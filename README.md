# aws-advanced-jdbc-wrapper-poc

[AWS Advanced JDBC Wrapper](https://github.com/aws/aws-advanced-jdbc-wrapper) の挙動(読み書きの振り分け・コネクションプール・フェイルオーバー)を、
ローカルの Docker 上で検証するための PoC です。Aurora PostgreSQL の Writer / Reader を PostgreSQL 18 で模倣しています。

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
| クラスター / リーダー / インスタンスエンドポイント | compose ネットワークの alias で RDS 形式のホスト名を提供 (`poc.cluster-…` が Writer、`poc.cluster-ro-…` が Reader) |
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

起動後の確認例:

```sh
curl localhost:8080/api/diag/connections   # 読み取りは reader、書き込みは writer に接続していること
curl 'localhost:8080/api/products?limit=3' # 商品の一覧 (Reader から取得)
```

| 用途 | URL |
|---|---|
| アプリ | http://localhost:8080 |
| Writer (psql 等) | localhost:5432 |
| Reader (psql 等) | localhost:5433 |

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
| `poc.pool.maximum-size` | 1インスタンスあたりの最大接続数 (Writer / Reader それぞれに適用) |
| `poc.pool.minimum-idle` | 1インスタンスあたりの最小アイドル接続数 |

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

## テスト

DB(Writer / Reader)への接続が必要なため、compose のネットワーク内で実行します。

```sh
docker compose --profile test run --rm --build app-test
```

`ConnectionPoolStartupTest`:

- 最初のアクセスで Writer / Reader の両方にコネクションプールが作られる
- Writer / Reader の接続がプールされ、再利用される(連続10回の接続で、バックエンドのプロセスが使い回される)
- 読み取り専用トランザクションは Reader に、書き込みトランザクションは Writer に接続する

環境変数 `RUN_DB_TESTS=true` のときだけ実行されます(`./gradlew test` を単体で実行した場合はスキップされます)。

## フェイルオーバーの確認

```sh
bash scripts/failover.sh               # Reader を昇格し、旧 Writer を停止、エンドポイントを付け替え
curl localhost:8080/api/diag/connections
bash scripts/reset-db.sh               # 元に戻す(docker/postgres/ のデータは削除される)
```

作者の環境(Docker Desktop on macOS)での確認結果: 直後の最初のリクエストは約15秒かかりましたが、エラーにならず成功し(wrapper の `failover2` が再接続)、
以降は昇格したインスタンスで読み書きできました。所要時間は環境や設定(`poc.wrapper.failover.*`)によって変わります。
旧 Writer を Reader として復帰させる処理は含めていません(`reset-db.sh` で作り直します)。

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
