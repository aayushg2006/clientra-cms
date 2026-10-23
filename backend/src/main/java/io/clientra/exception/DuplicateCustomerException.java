package io.clientra.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class DuplicateCustomerException extends ApiException {

    private final String field;
    private final Long existingId;
    private final String existingCode;

    public DuplicateCustomerException(String field, String value, Long existingId, String existingCode) {
        super(HttpStatus.CONFLICT, "DUPLICATE_CUSTOMER", "Customer with " + field + " " + value + " already exists");
        this.field = field;
        this.existingId = existingId;
        this.existingCode = existingCode;
    }
}
