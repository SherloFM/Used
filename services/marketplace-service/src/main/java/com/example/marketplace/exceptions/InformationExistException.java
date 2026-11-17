package com.example.marketplace.exceptions;

public class InformationExistException extends RuntimeException {
    public InformationExistException(String message) {
        super(message);
    }
}
