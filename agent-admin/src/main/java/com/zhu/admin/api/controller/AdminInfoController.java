package com.zhu.admin.api.controller;

import com.zhu.admin.api.dto.AdminInfo;
import com.zhu.common.dto.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理面自述。查询接口读的是数据库，不是用户进程内存。
 */
@RestController
public class AdminInfoController {

    /**
     * 返回进程角色。MySQL 关闭时查询接口是空列表，不把内存记录当成共享数据。
     */
    @GetMapping("/api/admin/info")
    public ApiResult<AdminInfo> info() {
        return ApiResult.ok(new AdminInfo(
                "admin",
                "审计和审批查询读的是 MySQL。开关关闭时列表为空，看不到用户进程内存里的记录。"));
    }
}
