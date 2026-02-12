package com.relix.servicebooking.common.exception;

import lombok.Getter;

@Getter
public class ForbiddenException extends RuntimeException {

    private final String code = "FORBIDDEN";

    public ForbiddenException(String message) {
        super(message);
    }
}
