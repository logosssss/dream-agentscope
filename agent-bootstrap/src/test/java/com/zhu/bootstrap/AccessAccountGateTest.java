package com.zhu.bootstrap;

import com.zhu.access.application.AccessAccountGate;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 对话以外的接入接口先过租户和用户门。不连接 MySQL。
 */
class AccessAccountGateTest {

    /** 其它测试可能把用户留在线程上。这里先清掉。 */
    @BeforeEach
    void resetIdentity() {
        UserContext.clear();
        TenantContext.clear();
    }

    /** 线程身份用完就清，避免串到下一条。 */
    @AfterEach
    void clearIdentity() {
        UserContext.clear();
        TenantContext.clear();
    }

    /** 没有用户时拒绝。门放行时不抛。没登记时拒绝。 */
    @Test
    void checksCurrentAccount() {
        AccessAccountGate open = new AccessAccountGate(tenantId -> {
        }, (tenantId, userId) -> {
        });
        assertThatThrownBy(open::requireActive)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        UserContext.set("u");
        TenantContext.set("t");
        assertThatCode(open::requireActive).doesNotThrowAnyException();

        AccessAccountGate closed = new AccessAccountGate(
                tenantId -> {
                },
                (tenantId, userId) -> {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "tenant user not found");
                });
        assertThatThrownBy(closed::requireActive)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }
}
