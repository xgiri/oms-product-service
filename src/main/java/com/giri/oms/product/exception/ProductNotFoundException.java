package com.giri.oms.product.exception;

import com.giri.oms.common.exception.ErrorCode;
import com.giri.oms.common.exception.ErrorCoded;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.NOT_FOUND)
public class ProductNotFoundException extends RuntimeException implements ErrorCoded {

    public ProductNotFoundException(Long id) {
        super(ErrorCode.PRODUCT_NOT_FOUND.formatMessage(id));
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.PRODUCT_NOT_FOUND;
    }
}
