package com.auction.bidding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GameMessage {
    private String type; // e.g., "JOIN", "START", "ACTION", "ERROR"
    private String gameType; // "uno" or "pool"
    private String roomId;
    private String username;
    private BigDecimal wager;
    private Object payload; // Any additional game-specific data
}
