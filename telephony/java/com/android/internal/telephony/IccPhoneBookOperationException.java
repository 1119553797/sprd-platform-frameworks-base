package com.android.internal.telephony;


public class IccPhoneBookOperationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    
    public static final int WRITE_OPREATION_FAILED = 0;
    public static final int EMAIL_CAPACITY_FULL = -1;
    public static final int ADN_CAPACITY_FULL = -2;
    public static final int OVER_NAME_MAX_LENGTH = -3;
    public static final int OVER_NUMBER_MAX_LENGTH = -4;
    public static final int LOAD_ADN_FAIL = -5;
    
    int mErrorCode = 0;
            
    public  IccPhoneBookOperationException() {
        
    }
    
    public  IccPhoneBookOperationException(String detailMessage) {
        super(detailMessage);
        // TODO Auto-generated constructor stub
    }
    
    public IccPhoneBookOperationException(Throwable throwable) {
        super(throwable);
        // TODO Auto-generated constructor stub
    }

    public IccPhoneBookOperationException(String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
        // TODO Auto-generated constructor stub
    }
    public IccPhoneBookOperationException(int errorCode, String detailMessage) {
        super(detailMessage);
        this.mErrorCode = errorCode;
    }
    
    public IccPhoneBookOperationException(int errorCode, String detailMessage, Throwable throwable) {
        super(detailMessage, throwable);
        this.mErrorCode = errorCode;
    }
   
}