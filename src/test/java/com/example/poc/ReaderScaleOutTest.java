package com.example.poc;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.poc.service.DiagService;
import com.example.poc.service.DiagService.TopologyRow;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Reader が複数あるときに、wrapper が両方を検出して使うことを確認する (Reader のスケールアウト)。
 *
 * <p>DB (Writer と Reader 2台) への接続が必要なため、環境変数 RUN_DB_TESTS=true のときだけ実行する。
 * compose ネットワーク内で実行する: docker compose --profile test run --rm app-test
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class ReaderScaleOutTest {

    private static final int REPEAT = 30;

    @Autowired
    private DiagService diagService;

    @Test
    void Writerから見たトポロジーにWriterと2台のReaderが含まれる() {
        List<String> members = diagService.topology().stream().map(TopologyRow::serverId).toList();

        assertThat(members).containsExactlyInAnyOrder("writer", "reader1", "reader2");
    }

    @Test
    void 読み取りが2台のReaderの両方に分散される() {
        // 選択方式は random (既定)。30回で片方が一度も選ばれない確率は約20億分の1
        Set<String> readers = new HashSet<>();
        for (int i = 0; i < REPEAT; i++) {
            readers.add(diagService.readConnection().instanceId());
        }

        assertThat(readers).containsExactlyInAnyOrder("reader1", "reader2");
    }
}
