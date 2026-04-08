package com.trawhile.web;

import com.trawhile.exception.BusinessRuleViolationException;
import com.trawhile.exception.EntityNotFoundException;
import com.trawhile.exception.InputValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    record Problem(String code, String message) {}

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Problem> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new Problem("UNAUTHENTICATED", ex.getMessage()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Problem> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new Problem("FORBIDDEN", ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Problem> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new Problem("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ResponseEntity<Problem> handleBusinessRule(BusinessRuleViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new Problem(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(InputValidationException.class)
    public ResponseEntity<Problem> handleValidation(InputValidationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new Problem(ex.getCode(), ex.getMessage()));
    }
}
