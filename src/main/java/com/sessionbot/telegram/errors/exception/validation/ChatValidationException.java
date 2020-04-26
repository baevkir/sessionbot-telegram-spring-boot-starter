package com.sessionbot.telegram.errors.exception.validation;

import com.sessionbot.telegram.errors.ErrorData;
import lombok.Data;

@Data
public class ChatValidationException extends RuntimeException {

    private ErrorData errorData;

    public ChatValidationException(ErrorData errorData) {
        super(errorData.getText());
        this.errorData = errorData;
    }
}
