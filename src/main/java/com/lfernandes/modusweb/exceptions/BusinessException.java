package com.lfernandes.modusweb.exceptions;

/** Violação de regra de negócio */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
