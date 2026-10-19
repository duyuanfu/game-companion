package com.game.companion.common.exception;

import com.game.companion.common.api.IResultCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {
    private final IResultCode errorCode;

    public ApiException(IResultCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public ApiException(String message) {
        super(message);
        this.errorCode = null;
    }
}
