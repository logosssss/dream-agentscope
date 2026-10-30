package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.AdminInfo;
import com.zhu.common.dto.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理面自述。不查用户进程里的内存审计和待审批，避免把另一份空列表当成真实数据。
 */
@RestController
public class AdminInfoController {

    /**
     * 返回进程角色。审计和审批目前在用户进程内存里，这个 JVM 读不到。
     */
    @GetMapping("/api/admin/info")
    public ApiResult<AdminInfo> info() {
        return ApiResult.ok(new AdminInfo(
                "admin",
                "管理进程与用户进程不是同一个 JVM，看不到用户进程内存里的审计记录和待审批列表。共享查询要等这些数据落库。"));
    }
}
