package com.selah;

public class BadImageException extends Exception {
    private String message;
    
    public BadImageException(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return this.message;
    }
}