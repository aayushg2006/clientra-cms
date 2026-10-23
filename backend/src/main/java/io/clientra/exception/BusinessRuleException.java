package io.clientra.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE", message);
    }

    public BusinessRuleException(String error, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, error, message);
    }
}
