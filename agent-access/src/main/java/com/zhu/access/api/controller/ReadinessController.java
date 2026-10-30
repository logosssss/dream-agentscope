package com.zhu.access.api.controller;

import com.zhu.access.application.AccessReadinessService;
import com.zhu.common.health.ReadinessSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户进程就绪检查。依赖不可用时返回 503，不改存活检查的结果。
 */
@RestController
public class ReadinessController {

    private final AccessReadinessService readiness;

    /** 汇总结果由应用服务给出，控制器只决定 HTTP 状态。 */
    public ReadinessController(AccessReadinessService readiness) {
        this.readiness = readiness;
    }

    /**
     * 未启用的 MySQL 和 Redis 记为 SKIPPED，总状态仍是 UP。
     * 有一项 DOWN 时 HTTP 503，调用方据此停止把流量打进来。
     */
    @GetMapping("/health/ready")
    public ResponseEntity<Map<String, String>> ready() {
        ReadinessSnapshot snapshot = readiness.snapshot();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("status", snapshot.status());
        body.putAll(snapshot.checks());
        if (snapshot.ready()) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(503).body(body);
    }
}
