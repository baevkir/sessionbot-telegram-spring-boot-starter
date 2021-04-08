package com.sessionbot.errors.exception.validation;

import com.sessionbot.errors.ErrorData;

public class DateValidationException extends ChatValidationException {
    public DateValidationException(ErrorData errorData) {
        super(errorData);
    }
}
