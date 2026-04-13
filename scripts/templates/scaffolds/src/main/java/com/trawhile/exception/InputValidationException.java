package com.trawhile.exception;

public class InputValidationException extends RuntimeException {

    private final String code;

    public InputValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
