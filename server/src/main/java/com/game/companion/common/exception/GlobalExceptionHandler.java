package com.game.companion.common.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.game.companion.common.api.CommonResult;
import com.game.companion.common.api.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(value = ApiException.class)
    public CommonResult<Object> handleApiException(ApiException e) {
        if (e.getErrorCode() != null) {
            return CommonResult.failed(e.getErrorCode());
        }
        return CommonResult.failed(e.getMessage());
    }

    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public CommonResult<Object> handleValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = null;
        if (bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
            }
        }
        return CommonResult.validateFailed(message);
    }

    @ExceptionHandler(value = BindException.class)
    public CommonResult<Object> handleBindException(BindException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = null;
        if (bindingResult.hasErrors()) {
            FieldError fieldError = bindingResult.getFieldError();
            if (fieldError != null) {
                message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
            }
        }
        return CommonResult.validateFailed(message);
    }

    @ExceptionHandler(value = NotLoginException.class)
    public CommonResult<Object> handleNotLoginException(NotLoginException e) {
        return CommonResult.failed(ResultCode.UNAUTHORIZED);
    }

    @ExceptionHandler(value = NotRoleException.class)
    public CommonResult<Object> handleNotRoleException(NotRoleException e) {
        return CommonResult.failed(ResultCode.FORBIDDEN);
    }

    @ExceptionHandler(value = Exception.class)
    public CommonResult<Object> handleException(Exception e) {
        log.error("系统未知异常: ", e);
        return CommonResult.failed(e.getMessage());
    }
}
