package io.clientra.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Base class for business errors that map to a specific HTTP status and error code. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    public ApiException(HttpStatus status, String error, String message) {
        super(message);
        this.status = status;
        this.error = error;
    }
}
