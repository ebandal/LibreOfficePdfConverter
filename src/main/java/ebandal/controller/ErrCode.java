package ebandal.controller;

public enum ErrCode {
	UNDEFINED(10000, "정의되지 않은 에러입니다."),
	
	// 미디어 세그먼트 변환
	SOFFICE_NOT_INSTALLED(10001, "SOFFICE가 설치되지 않았습니다."),
	SOFFICE_INSTALLING_FAILURE(10003, "SOFFICE설치가 실패했습니다."),
	SOFFICE_PROCESS_INTERRUPTED(10004, "PDF 변환중 중단되었습니다(인터럽트)."),
	SOFFICE_PROCESS_TERMINATED_ABNORMALLY(10005, "PDF 변환이 비정상적으로 종료되었습니다."),
	SOFFICE_PROCESS_TERMINATED_IOEXCEPTION(10006, "PDF 변환중 예외가 발생했습니다."),

	// 라이선스
	LICENSE_NOT_EXISTS(30001, "라이선스를 찾을수 없습니다."),
	LICENSE_EXPIRED(30002, "라이선스가 만료되었습니다."),

	KEY_IS_EMPTY(40001, "키 값이 존재하지 않습니다."),
	FILE_NOT_FOUND(40002, "파일이 존재하지 않습니다."),
	
	// 설정값
	CONFIG_NOT_DEFINED(400001, "설정값이 정의되지 않았습니다."),

	UNKNWON_FAILURE(90000, "알 수 없는 이유로 실패했습니다."),
	;
	
	private int errCode;
    private String reason;
	
    public String getErrMsg() {
    	return new String(reason);
    }

    public int getErrCode() {
    	return errCode;
    }
    
    private ErrCode(int errCode, String reason) { 
    	this.errCode = errCode;
        this.reason = reason; 
    }
    
}