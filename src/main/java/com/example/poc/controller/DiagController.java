package com.example.poc.controller;

import com.example.poc.service.DiagService;
import com.example.poc.service.DiagService.ConnectionInfo;
import com.example.poc.service.DiagService.ProbeResult;
import com.example.poc.service.DiagService.TopologyRow;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** wrapper の挙動 (読み書きの振り分け・プール・トポロジー・レプリカラグ) を確認する API。 */
@RestController
@RequestMapping("/api/diag")
public class DiagController {

    private final DiagService service;

    public DiagController(DiagService service) {
        this.service = service;
    }

    /** 読み取り専用トランザクションと書き込みトランザクションの接続先を同時に返す。 */
    @GetMapping("/connections")
    public Map<String, ConnectionInfo> connections() {
        return Map.of("readOnlyTransaction", service.readConnection(), "writeTransaction", service.writeConnection());
    }

    /** Writer / Reader それぞれへのクライアント接続数 (内部プールが保持している接続を含む)。 */
    @GetMapping("/pools")
    public Map<String, Long> pools() {
        return service.poolConnections();
    }

    /** aurora_replica_status() が返すトポロジー。 */
    @GetMapping("/topology")
    public List<TopologyRow> topology() {
        return service.topology();
    }

    /** 書き込み直後に Reader から読み取り、レプリカラグの有無を確認する。 */
    @PostMapping("/write-then-read")
    public ProbeResult writeThenRead() {
        return service.writeThenRead();
    }
}
