package com.auction.bidding.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class BidRequest {
    
    @NotNull(message = "Item ID cannot be null")
    private Long itemId;
    
    // Filled from the authenticated Principal by the controller; never trust a client-supplied name.
    private String bidderName;
    
    @NotNull(message = "Bid amount cannot be null")
    @DecimalMin(value = "0.01", message = "Bid amount must be greater than 0")
    private BigDecimal amount;
}
