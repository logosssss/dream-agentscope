package com.zhu.admin.api.controller.health;

import com.zhu.admin.application.AdminReadinessService;
import com.zhu.common.health.ReadinessSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程就绪检查。不探测用户进程和模型。MySQL 不可用时返回 503，存活检查仍为 UP。
 */
@RestController
public class AdminReadinessController {

    private final AdminReadinessService readiness;

    /** 汇总结果由应用服务给出，控制器只决定 HTTP 状态。 */
    public AdminReadinessController(AdminReadinessService readiness) {
        this.readiness = readiness;
    }

    /**
     * 默认 MySQL 关闭，这项为 SKIPPED，HTTP 200。
     * 开关打开但库不可用时 HTTP 503。
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
