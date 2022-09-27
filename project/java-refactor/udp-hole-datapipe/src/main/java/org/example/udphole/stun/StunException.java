package org.example.udphole.stun;

public class StunException extends RuntimeException{
    public StunException(Throwable baseException){
        super(baseException);
    }
    public StunException(String message){
        super(message);
    }
}