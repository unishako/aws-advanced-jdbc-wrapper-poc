package com.example.poc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.poc.service.DiagService;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Writer / Reader のコネクションプール (wrapper の内部プール) が作成され、機能していることを確認する。
 *
 * <p>DB (Writer/Reader) への接続が必要なため、環境変数 RUN_DB_TESTS=true のときだけ実行する。
 * compose ネットワーク内で実行する: docker compose --profile test run --rm app-test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class ConnectionPoolStartupTest {

    private static final int REPEAT = 10;

    @Autowired
    private DiagService diagService;

    @Value("${poc.pool.minimum-idle}")
    private int minimumIdle;

    @Test
    void 最初のアクセスでWriterとReaderの両方にコネクションプールが作成される() {
        // wrapper の内部プールは、そのインスタンスへ最初に接続したときに作られる(遅延作成)。
        // poolConnections() は Writer / Reader の両方に接続するので、ここで両方のプールが作られ、
        // 最小アイドル数の接続が保持される
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            Map<String, Long> connections = diagService.poolConnections();

            assertThat(connections.get("writer")).isGreaterThanOrEqualTo((long) minimumIdle);
            assertThat(connections.get("reader")).isGreaterThanOrEqualTo((long) minimumIdle);
        });
    }

    @Test
    void Writerの接続はプールされ再利用される() {
        assertThat(distinctPids(diagService::writerBackendPid)).hasSizeLessThan(REPEAT);
    }

    @Test
    void Readerの接続はプールされ再利用される() {
        assertThat(distinctPids(diagService::readerBackendPid)).hasSizeLessThan(REPEAT);
    }

    @Test
    void 読み取り専用トランザクションはReaderに接続する() {
        var connection = diagService.readConnection();

        assertThat(connection.instanceId()).isEqualTo("reader");
        assertThat(connection.reader()).isTrue();
    }

    @Test
    void 書き込みトランザクションはWriterに接続する() {
        var connection = diagService.writeConnection();

        assertThat(connection.instanceId()).isEqualTo("writer");
        assertThat(connection.reader()).isFalse();
    }

    /**
     * 連続して接続し、使われたバックエンドプロセス ID の種類を返す。
     * プールがなければ毎回新しい物理接続になり REPEAT 種類になる。プールがあれば再利用されて少なくなる。
     */
    private Set<Integer> distinctPids(IntSupplier backendPid) {
        Set<Integer> pids = new HashSet<>();
        for (int i = 0; i < REPEAT; i++) {
            pids.add(backendPid.getAsInt());
        }
        return pids;
    }
}
