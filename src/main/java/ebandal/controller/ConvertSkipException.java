package ebandal.controller;

public class ConvertSkipException extends RuntimeException {
	
	public ConvertSkipException(String message) {
		super(message);
	}

	public ConvertSkipException(String message, Throwable cause) {
		super(message, cause);
	}
}