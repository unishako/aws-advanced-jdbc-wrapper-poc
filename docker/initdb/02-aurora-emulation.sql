-- Aurora PostgreSQL の関数を模倣する。
-- AWS Advanced JDBC Wrapper は Aurora 判定とトポロジー取得に以下を使用する:
--   pg_catalog.aurora_replica_status()
--   pg_catalog.aurora_db_instance_identifier()
-- インスタンス名はサーバー起動オプション (-c poc.instance_id=..., -c poc.peer_id=...) で与える。
-- 昇格(フェイルオーバー)後も、自身の pg_is_in_recovery() から Writer/Reader を判定する。

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
  -- 自分自身
  SELECT pg_catalog.current_setting('poc.instance_id'),
         CASE WHEN pg_catalog.pg_is_in_recovery() THEN 'reader-session' ELSE 'MASTER_SESSION_ID' END,
         0::double precision,
         CASE WHEN pg_catalog.pg_is_in_recovery()
              THEN COALESCE(EXTRACT(EPOCH FROM (pg_catalog.now() - pg_catalog.pg_last_xact_replay_timestamp())) * 1000, 0)
              ELSE NULL END,
         pg_catalog.now()
  UNION ALL
  -- もう一方のインスタンス (自分が Writer なら相手は Reader、自分が Reader なら相手は Writer)
  SELECT pg_catalog.current_setting('poc.peer_id'),
         CASE WHEN pg_catalog.pg_is_in_recovery() THEN 'MASTER_SESSION_ID' ELSE 'reader-session' END,
         0::double precision,
         CASE WHEN pg_catalog.pg_is_in_recovery() THEN NULL ELSE 0 END,
         pg_catalog.now()
$$;
