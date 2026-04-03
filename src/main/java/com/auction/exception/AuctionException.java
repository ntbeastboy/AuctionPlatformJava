package com.auction.exception;

import com.auction.util.ErrorCode;

public class AuctionException extends RuntimeException {
    private final ErrorCode errorCode;

    public AuctionExecption(ErrorCode errorcode)
    {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    public AuctionException(ErrorCode errorcode, Throwable cause)
    {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode()
    {
        return errorCode;
    }
}