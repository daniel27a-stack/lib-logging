package cl.pirca.lib.logging.testapp;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Handles TestFailure without logging, leaving the exception in the request attribute (the lib-errors contract). */
@RestControllerAdvice
public class TestAdvice {

    @ExceptionHandler(TestFailure.class)
    ResponseEntity<String> handle(TestFailure failure, HttpServletRequest request) {
        request.setAttribute("cl.pirca.lib.errors.EXCEPTION", failure);
        return ResponseEntity.status(failure.status()).body(failure.getMessage());
    }
}
