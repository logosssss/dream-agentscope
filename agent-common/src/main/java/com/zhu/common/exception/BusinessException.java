package com.zhu.common.exception;

/**
 * 可预期的业务失败。调用方用 {@link #errorCode()} 决定对外文案，不靠异常信息做分支。
 */
public final class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * @param errorCode 稳定错误码
     * @param message   给人看的说明，可以随语言调整
     */
    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /** 本次失败对应的错误码。 */
    public ErrorCode errorCode() {
        return errorCode;
    }
}
