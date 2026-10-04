package com.inkspace.common.exception;

import com.inkspace.common.api.ErrorCode;

/**
 * 业务异常：Service 层对"可预期失败"直接抛出，由全局异常处理器统一转成 Result。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
