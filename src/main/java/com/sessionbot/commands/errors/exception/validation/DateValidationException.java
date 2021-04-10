package com.sessionbot.commands.errors.exception.validation;

import com.sessionbot.commands.errors.ErrorData;

public class DateValidationException extends ChatValidationException {
    public DateValidationException(ErrorData errorData) {
        super(errorData);
    }
}
