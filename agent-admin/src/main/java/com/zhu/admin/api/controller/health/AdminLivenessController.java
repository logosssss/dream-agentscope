package com.zhu.admin.api.controller.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理进程存活检查。只说明这个进程还在，不探测用户进程、数据库或模型。
 */
@RestController
public class AdminLivenessController {

    /** 管理进程存活即返回 UP。 */
    @GetMapping("/health/live")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }
}
