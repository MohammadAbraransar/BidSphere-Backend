package com.auction.bidding.dto;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class PaymentRequest {
    
    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "1.00", message = "Minimum deposit is 1.00")
    private BigDecimal amount;
    
    private String currency = "usd";
}
