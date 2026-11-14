package com.example.poc.service;

import com.example.poc.repository.ProductRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** wrapper の振り分け・プール・レプリケーションの挙動を確認するための診断サービス。 */
@Service
public class DiagService {

    /** 接続先インスタンスの情報。 */
    public record ConnectionInfo(String instanceId, boolean inRecovery, String serverAddress) {
        /** Reader (スタンバイ) に接続していれば true。 */
        public boolean reader() {
            return inRecovery;
        }
    }

    /** aurora_replica_status() の1行。 */
    public record TopologyRow(String serverId, String sessionId, Double replicaLagMs) {
    }

    /** 書き込み直後に読み取った結果 (レプリカラグの確認用)。 */
    public record ProbeResult(
            int writtenStock,
            int readStock,
            boolean consistent,
            String writerInstance,
            String readerInstance) {
    }

    private final JdbcTemplate jdbc;
    private final ProductRepository productRepository;
    private final TransactionTemplate writeTx;
    private final TransactionTemplate readTx;

    public DiagService(JdbcTemplate jdbc, ProductRepository productRepository, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.productRepository = productRepository;
        this.writeTx = new TransactionTemplate(txManager);
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
    }

    public ConnectionInfo readConnection() {
        return readTx.execute(status -> currentConnection());
    }

    public ConnectionInfo writeConnection() {
        return writeTx.execute(status -> currentConnection());
    }

    private ConnectionInfo currentConnection() {
        return jdbc.queryForObject(
                "select pg_catalog.aurora_db_instance_identifier(), pg_catalog.pg_is_in_recovery(),"
                        + " pg_catalog.inet_server_addr()::text",
                (rs, i) -> new ConnectionInfo(rs.getString(1), rs.getBoolean(2), rs.getString(3)));
    }

    /** 書き込みトランザクション側から見た Aurora トポロジー (aurora_replica_status)。 */
    public List<TopologyRow> topology() {
        return writeTx.execute(status -> jdbc.query(
                "select server_id, session_id, replica_lag_in_msec from pg_catalog.aurora_replica_status()"
                        + " order by server_id",
                (rs, i) -> new TopologyRow(rs.getString(1), rs.getString(2), rs.getObject(3, Double.class))));
    }

    /**
     * 各インスタンス (writer / reader) へのクライアント接続数 (サーバー側の pg_stat_activity)。
     * wrapper の内部プールが作られていれば、最小アイドル数 (cp-MinimumIdle) 以上の接続が保持される。
     */
    public Map<String, Long> poolConnections() {
        String sql = "select count(*) from pg_catalog.pg_stat_activity"
                + " where backend_type = 'client backend' and datname = pg_catalog.current_database()";
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("writer", writeTx.execute(status -> jdbc.queryForObject(sql, Long.class)));
        counts.put("reader", readTx.execute(status -> jdbc.queryForObject(sql, Long.class)));
        return counts;
    }

    /** 接続先のバックエンドプロセス ID。プールされていれば、連続して呼んでも同じ物理接続が再利用される。 */
    public int writerBackendPid() {
        return writeTx.execute(status -> jdbc.queryForObject("select pg_catalog.pg_backend_pid()", Integer.class));
    }

    public int readerBackendPid() {
        return readTx.execute(status -> jdbc.queryForObject("select pg_catalog.pg_backend_pid()", Integer.class));
    }

    /**
     * 商品1の在庫を Writer で更新・コミットした直後に、Reader から読み取る。
     * レプリカラグがあれば consistent が false になる。
     */
    public ProbeResult writeThenRead() {
        record Written(int stock, String instance) {
        }
        Written written = writeTx.execute(status -> {
            productRepository.addStock(1L, 1);
            return new Written(productRepository.findById(1L).orElseThrow().stock(), currentConnection().instanceId());
        });
        var read = readTx.execute(status ->
                new Written(productRepository.findById(1L).orElseThrow().stock(), currentConnection().instanceId()));
        return new ProbeResult(written.stock(), read.stock(), written.stock() == read.stock(),
                written.instance(), read.instance());
    }
}
