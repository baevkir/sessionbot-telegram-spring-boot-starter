package com.sessionbot.telegram.errors.exception.validation;

import com.sessionbot.telegram.errors.ErrorData;

public class DateValidationException extends ChatValidationException {
    public DateValidationException(ErrorData errorData) {
        super(errorData);
    }
}
