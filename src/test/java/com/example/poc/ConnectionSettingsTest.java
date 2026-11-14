package com.example.poc;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * プールが作る接続の初期状態 (自動コミット無効 / READ COMMITTED) が、Writer・Reader の両方で効いていることを確認する。
 *
 * <p>DB (Writer/Reader) への接続が必要なため、環境変数 RUN_DB_TESTS=true のときだけ実行する。
 * compose ネットワーク内で実行する: docker compose --profile test run --rm app-test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class ConnectionSettingsTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void Writerの接続は自動コミットが無効でREAD_COMMITTEDである() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getAutoCommit()).isFalse();
            assertThat(connection.isReadOnly()).isFalse();
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            assertThat(serverState(connection)).isEqualTo("writer:read committed");
            connection.rollback();
        }
    }

    @Test
    void Readerに切り替えても自動コミットが無効でREAD_COMMITTEDである() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setReadOnly(true);

            assertThat(connection.isReadOnly()).isTrue();
            assertThat(connection.getAutoCommit()).isFalse();
            assertThat(connection.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
            // 実際に Reader に接続していて、サーバー側の分離レベルも READ COMMITTED であること
            assertThat(serverState(connection)).isEqualTo("reader:read committed");
            connection.rollback();
        }
    }

    /** 接続先インスタンス名とサーバー側のトランザクション分離レベルを返す。 */
    private String serverState(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "select pg_catalog.aurora_db_instance_identifier() || ':' || "
                                + "pg_catalog.current_setting('transaction_isolation')")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
