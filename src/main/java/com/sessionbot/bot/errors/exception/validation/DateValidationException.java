package com.sessionbot.bot.errors.exception.validation;

import com.sessionbot.bot.errors.ErrorData;

public class DateValidationException extends ChatValidationException {
    public DateValidationException(ErrorData errorData) {
        super(errorData);
    }
}
