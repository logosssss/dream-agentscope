package com.zhu.access.api.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LivenessController {

    @GetMapping("/health/live")
    public Map<String, String> live() {
        return Map.of("status", "UP");
    }
}
