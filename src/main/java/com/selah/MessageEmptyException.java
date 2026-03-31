package com.selah;

public class MessageEmptyException extends Exception {

    private String message;
    
    public MessageEmptyException(String message) {
        this.message = message;
    }
    
    public String getMessage() {
        return this.message;
    }
}
