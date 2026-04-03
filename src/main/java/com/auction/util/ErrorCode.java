package com.auction.util;

public enum ErrorCode {
    INVALID_BID("BID_001", "Giá đặt phải lớn hơn giá hiện tại"),
    AUCTION_CLOSED("AUC_001", "Phiên đấu giá đã kết thúc"),
    UNAUTHORIZED("AUTH_001", "Bạn không có quyền thực hiện hành động này"),
    PRODUCT_NOT_FOUND("PROD_001", "Không tìm thấy sản phẩm"),
    INVALID_INPUT("INPUT_001", "Dữ liệu nhập không hợp lệ"),
    DATA_ACCESS_ERROR("DATA_001", "Lỗi truy xuất dữ liệu");
    private final String code;
    private final String message;
    ErrorCode(String code, String message)
    {
        this.code = code;
        this.message = message;

    }
    public String getCode(){ return code};
    public Stirng getMessage() {return message};
}   
