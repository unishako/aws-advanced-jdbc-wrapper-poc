-- Aurora PostgreSQL の関数を模倣する。
-- AWS Advanced JDBC Wrapper は Aurora 判定とトポロジー取得に以下を使用する:
--   pg_catalog.aurora_replica_status()
--   pg_catalog.aurora_db_instance_identifier()
-- サーバー起動オプションで与える設定:
--   -c poc.instance_id=<自分のインスタンス名>
--   -c poc.members=<全インスタンス名をカンマ区切り。先頭が Writer>   (例: writer,reader1,reader2)
-- 昇格(フェイルオーバー)後も、自身の pg_is_in_recovery() から Writer/Reader を判定する。
-- Writer から呼ばれた場合は、レプリケーションが切れている Reader を結果から除く (停止した Reader がトポロジーから外れる)。

-- pg_catalog 配下への作成にはシステムカタログ変更の許可が必要
SET allow_system_table_mods = on;

CREATE OR REPLACE FUNCTION pg_catalog.aurora_db_instance_identifier()
RETURNS text
LANGUAGE sql STABLE
AS $$ SELECT pg_catalog.current_setting('poc.instance_id') $$;

CREATE OR REPLACE FUNCTION pg_catalog.aurora_replica_status()
RETURNS TABLE (
  server_id text,
  session_id text,
  cpu double precision,
  replica_lag_in_msec double precision,
  last_update_timestamp timestamptz
)
LANGUAGE sql STABLE
AS $$
  -- Writer のインスタンス名: 自分が昇格済み(スタンバイでない)なら自分、スタンバイなら members の先頭
  WITH writer(id) AS (
    SELECT CASE WHEN pg_catalog.pg_is_in_recovery()
                THEN (pg_catalog.string_to_array(pg_catalog.current_setting('poc.members'), ','))[1]
                ELSE pg_catalog.current_setting('poc.instance_id') END
  )
  SELECT m.id,
         CASE WHEN m.id = w.id THEN 'MASTER_SESSION_ID' ELSE 'reader-session' END,
         0::double precision,
         -- Writer は NULL。自分が Reader なら実際の遅延、ほかの Reader は 0
         CASE WHEN m.id = w.id THEN NULL
              WHEN m.id = pg_catalog.current_setting('poc.instance_id')
                THEN COALESCE(EXTRACT(EPOCH FROM (pg_catalog.now() - pg_catalog.pg_last_xact_replay_timestamp())) * 1000, 0)
              ELSE 0 END,
         pg_catalog.now()
  FROM pg_catalog.unnest(pg_catalog.string_to_array(pg_catalog.current_setting('poc.members'), ',')) AS m(id),
       writer w
  WHERE
        -- Reader から見た場合は、他のインスタンスの死活が分からないため、全メンバーを返す
        pg_catalog.pg_is_in_recovery()
        -- Writer から見た場合: 自分自身は常に返す
     OR m.id = w.id
        -- Writer から見た場合: レプリケーションが切れている (スロットが active でない) Reader は返さない。
        -- 本物の Aurora で、更新が止まった Reader がトポロジーから外れるのを模倣する (スロット名は <メンバー名>_slot)
     OR EXISTS (SELECT 1 FROM pg_catalog.pg_replication_slots s
                WHERE s.slot_name = m.id || '_slot' AND s.active)
$$;
