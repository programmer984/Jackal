package org.example;

public class HttpException extends Exception{

	/**
	 * 
	 */
	private static final long serialVersionUID = 1902254619591059252L;
	public HttpException(Throwable baseException){
        super(baseException);
    }
    public HttpException(String message){
        super(message);
    }
}
