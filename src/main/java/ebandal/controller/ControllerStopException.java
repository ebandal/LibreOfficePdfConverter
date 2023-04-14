package ebandal.controller;

public class ControllerStopException extends RuntimeException {
	private static final long serialVersionUID = -4101657827454413777L;
	private ErrCode errCode;

	public ControllerStopException() {
        super();
    }

	public ControllerStopException(ErrCode errCode) {
        super(errCode.toString());
        this.errCode = errCode;
	}

	public ControllerStopException(ErrCode errCode, String messsage) {
        super(messsage);
        this.errCode = errCode;
	}
	
	public ControllerStopException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

    public ControllerStopException(String message, Throwable cause) {
        super(message, cause);
    }

    public ControllerStopException(String message) {
        super(message);
    }

    public ControllerStopException(Throwable cause) {
        super(cause);
    }
    
    public ErrCode getReason() {
    	return errCode==null?ErrCode.UNDEFINED:errCode;
    }
}
