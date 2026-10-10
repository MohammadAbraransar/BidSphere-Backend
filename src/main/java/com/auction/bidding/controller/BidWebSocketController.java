package com.auction.bidding.controller;

import com.auction.bidding.dto.BidRequest;
import com.auction.bidding.model.User;
import com.auction.bidding.repository.UserRepository;
import com.auction.bidding.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class BidWebSocketController {
    private final AuctionService auctionService;
    private final UserRepository userRepository;

    @MessageMapping("/placeBid")
    public void processBid(@Payload BidRequest bidRequest, Principal principal) {
        if (principal == null || bidRequest == null) return;
        User user = userRepository.findByUsername(principal.getName()).orElse(null);
        // Only buyer accounts may bid. Never trust a bidder name sent by the browser.
        if (user == null || !"ROLE_USER".equals(user.getRole())) return;
        bidRequest.setBidderName(principal.getName());
        try {
            auctionService.placeBid(bidRequest);
        } catch (RuntimeException ex) {
            // REST bidding provides detailed validation errors; WebSocket requests are rejected safely.
        }
    }
}
