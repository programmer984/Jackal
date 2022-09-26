package org.example;

public class JsonConvertException extends Exception{
    public JsonConvertException(Throwable baseException){
        super(baseException);
    }
}
