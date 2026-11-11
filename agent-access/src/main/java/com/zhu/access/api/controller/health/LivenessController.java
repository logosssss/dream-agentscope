package com.zhu.access.api.controller.health;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存活检查。只说明进程还在，不探测数据库或模型，避免依赖抖动被当成进程死亡。
 */
@RestController
public class LivenessController {

    /** 进程存活即返回 UP。 */
    @GetMapping("/health/live")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }
}
