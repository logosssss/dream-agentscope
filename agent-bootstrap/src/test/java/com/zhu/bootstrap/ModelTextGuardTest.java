package com.zhu.bootstrap;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.domain.ModelTextGuard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 进出模型的文本检查不连接外部服务。用户正文里的花括号不算模板。
 */
class ModelTextGuardTest {

    /** 系统提示词留下占位符时拒绝。用户输入里的同样写法可以进入模型。 */
    @Test
    void systemPromptPlaceholdersAreRejected() {
        assertThatThrownBy(() -> ModelTextGuard.beforeCall("你好 {{name}}", "问题", 100))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatCode(() -> ModelTextGuard.beforeCall("你好", "用户写了 {{name}}", 100))
                .doesNotThrowAnyException();
    }

    /** 超过字符上限时拒绝。上限小于 1 时改用默认值，短文本仍放行。 */
    @Test
    void userTextLengthIsLimited() {
        assertThatThrownBy(() -> ModelTextGuard.beforeCall(".", "abcd", 3))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatCode(() -> ModelTextGuard.beforeCall(".", "abcd", 0)).doesNotThrowAnyException();
    }

    /** 最终回复里的占位符不返回给调用方。 */
    @Test
    void outboundDropsPlaceholders() {
        assertThat(ModelTextGuard.outbound("结果 {{name}}")).isEmpty();
        assertThat(ModelTextGuard.outbound("结果")).isEqualTo("结果");
    }
}
