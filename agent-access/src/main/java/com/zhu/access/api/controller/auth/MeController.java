package com.zhu.access.api.controller.auth;

import com.zhu.access.api.dto.CurrentUser;
import com.zhu.access.application.auth.CurrentUserQuery;
import com.zhu.common.dto.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户查看这次请求的身份。不签发令牌，也不读剩余 TTL。
 */
@RestController
public class MeController {

    private final CurrentUserQuery users;

    /** 身份来自线程上下文和可选的登记行。 */
    public MeController(CurrentUserQuery users) {
        this.users = users;
    }

    /**
     * 只返回这次请求已经核对过的租户和用户。没有密码，也不发 JWT。
     */
    @GetMapping("/api/me")
    public ApiResult<CurrentUser> current() {
        return ApiResult.ok(users.current());
    }
}
