package com.orasa.backend.dto.payment;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PayloroRequest {
    private String merchantNo;
    private String merchantOrderNo;
    private String payAmount; 
    private String description;
    private String method;
    private String name;
    private String mobile;
    private String email;
    private String notifyUrl;
    private String sign;
}
