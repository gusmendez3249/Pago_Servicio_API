package com.proyecto.servicios.exception;

import com.proyecto.servicios.enums.ErrorCode;
import lombok.Getter;

@Getter
public class IntegrationException extends RuntimeException {

    private final ErrorCode errorCode;

    public IntegrationException(ErrorCode errorCode) {
        super(errorCode != null
                ? errorCode.getMessage()
                : ErrorCode.INTERNAL_ERROR.getMessage());

        this.errorCode = errorCode != null
                ? errorCode
                : ErrorCode.INTERNAL_ERROR;
    }

    public IntegrationException(
            ErrorCode errorCode,
            Throwable cause) {

        super(
                errorCode != null
                        ? errorCode.getMessage()
                        : ErrorCode.INTERNAL_ERROR.getMessage(),
                cause
        );

        this.errorCode = errorCode != null
                ? errorCode
                : ErrorCode.INTERNAL_ERROR;
    }
}