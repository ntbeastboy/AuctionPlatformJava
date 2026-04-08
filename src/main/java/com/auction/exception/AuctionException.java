package com.auction.exception;

import com.auction.util.ErrorCode;

public class AuctionException extends RuntimeException {
    private final ErrorCode errorCode;

    public AuctionException(ErrorCode errorcode)
    {
        super(errorcode.getMessage());
        this.errorCode = errorcode;
    }
    public AuctionException(ErrorCode errorcode, Throwable cause)
    {
        super(errorcode.getMessage(), cause);
        this.errorCode = errorcode;
    }
    public ErrorCode getErrorCode()
    {
        return errorCode;
    }
}