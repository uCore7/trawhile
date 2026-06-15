package com.trawhile.adapter.inbound.web;

import com.trawhile.adapter.outbound.logging.AppLogger;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class MalformedRequestBodyLogger {

    private static final AppLogger log = AppLogger.getLogger(MalformedRequestBodyLogger.class);

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail onMalformedBody(HttpMessageNotReadableException ex,
                                         HttpServletRequest request) {
        // SR-00-C14.F01: ex.getMessage() can include request-body snippets.
        log.warn("request.bodyParseFailure",
                new AppLogger.RawField("method", request.getMethod()),
                new AppLogger.RawField("path", request.getRequestURI()),
                new AppLogger.RawField("exceptionClass", ex.getClass().getSimpleName()));

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed request body");
        return problem;
    }
}
