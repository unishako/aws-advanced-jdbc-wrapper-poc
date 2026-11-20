package com.example.poc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.poc.service.DiagService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Writer を停止したときに、wrapper (failover2) が新しい Writer へ再接続することを確認する。
 *
 * <p>scripts/failover.sh で、旧 Writer の停止 → reader1 の昇格 → reader2 の付け替え、を行う。
 * DB の状態が元に戻らない破壊的なテストのため、環境変数 RUN_FAILOVER_TESTS=true のときだけ実行する。
 * 通常のテスト (app-test) とは別のサービスで、docker の操作ができる状態で実行する:
 * docker compose --profile failover-test run --rm app-failover-test
 * 実行後は scripts/reset-db.sh で DB を作り直すこと。
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_FAILOVER_TESTS", matches = "true")
class FailoverTest {

    private static final Duration FAILOVER_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration SCRIPT_TIMEOUT = Duration.ofMinutes(3);
    private static final int REPEAT = 20;

    @Autowired
    private DiagService diagService;

    @Test
    @Timeout(value = 8, unit = TimeUnit.MINUTES)
    void Writerを停止すると新しいWriterへ再接続し読み書きが続く() throws Exception {
        // フェイルオーバー前: 書き込みは writer、読み取りは Reader。
        // ここで wrapper が接続 (内部プール) を確立するので、停止時には既存の接続が失われる状況になる
        assertThat(diagService.writeConnection().instanceId()).isEqualTo("writer");
        assertThat(diagService.readConnection().reader()).isTrue();

        runFailoverScript();

        // フェイルオーバー中は、接続の切断や再接続の通知 (FailoverSuccessSQLException など) が例外として返る。
        // wrapper が新しい Writer (reader1) を見つけ、書き込みが通るようになるまで再試行する
        await().atMost(FAILOVER_TIMEOUT).pollInterval(Duration.ofSeconds(1)).ignoreExceptions()
                .untilAsserted(() -> {
                    var connection = diagService.writeConnection();

                    assertThat(connection.instanceId()).isEqualTo("reader1");
                    assertThat(connection.reader()).as("昇格後は Reader ではない").isFalse();
                });

        // 新しい Writer へ実際に書き込め、残りの Reader (reader2) がそれに追従する
        await().atMost(FAILOVER_TIMEOUT).pollInterval(Duration.ofSeconds(1)).ignoreExceptions()
                .untilAsserted(() -> {
                    var probe = diagService.writeThenRead();

                    assertThat(probe.writerInstance()).isEqualTo("reader1");
                    assertThat(probe.readerInstance()).isEqualTo("reader2");
                    assertThat(probe.consistent()).as("新しい Writer の書き込みが reader2 に反映される").isTrue();
                });

        // 読み取りは、停止した旧 Writer には向かわず、すべて成功する。
        // 昇格した reader1 (新しい Writer) が読み取りに使われることもあるため、reader2 だけとは限らない
        // 昇格直後は、接続の切断や再接続の通知で例外になることもあるため、1回ごとに成功するまで再試行する
        Set<String> readers = new HashSet<>();
        for (int i = 0; i < REPEAT; i++) {
            readers.add(await().atMost(FAILOVER_TIMEOUT).pollInterval(Duration.ofSeconds(1)).ignoreExceptions()
                    .until(() -> diagService.readConnection().instanceId(), instanceId -> true));
        }
        assertThat(readers).isNotEmpty().doesNotContain("writer").isSubsetOf("reader1", "reader2");
    }

    /** scripts/failover.sh を実行し、正常終了することを確認する。出力はテストのログに残す。 */
    private void runFailoverScript() throws Exception {
        // 出力はファイルに書き出す。パイプを読みながら待つと、docker がハングしたときにタイムアウトできないため
        Path outputFile = Files.createTempFile("failover", ".log");
        try {
            Process process = new ProcessBuilder("bash", "scripts/failover.sh")
                    .redirectErrorStream(true)
                    .redirectOutput(outputFile.toFile())
                    .start();
            boolean finished = process.waitFor(SCRIPT_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
            }
            String output = Files.readString(outputFile, StandardCharsets.UTF_8);
            System.out.println(output);

            assertThat(finished).as("failover.sh が %s 以内に終了する。出力:%n%s", SCRIPT_TIMEOUT, output).isTrue();
            assertThat(process.exitValue()).as("failover.sh の終了コード。出力:%n%s", output).isZero();
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }
}
