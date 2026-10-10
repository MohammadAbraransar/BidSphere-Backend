package com.auction.bidding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BidMessage {
    private Long itemId;
    private String bidderName;
    private BigDecimal currentPrice;
    private LocalDateTime bidTime;
    private String message;
}
