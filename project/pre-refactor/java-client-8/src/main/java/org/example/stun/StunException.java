package org.example.stun;

public class StunException extends RuntimeException{
    public StunException(Throwable baseException){
        super(baseException);
    }
    public StunException(String message){
        super(message);
    }
}