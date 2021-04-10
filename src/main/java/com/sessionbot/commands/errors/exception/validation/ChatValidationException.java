package com.sessionbot.commands.errors.exception.validation;

import com.sessionbot.commands.errors.ErrorData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatValidationException extends RuntimeException {

    private ErrorData errorData;

    public ChatValidationException(ErrorData errorData) {
        super(errorData.getText());
        this.errorData = errorData;
    }
}
