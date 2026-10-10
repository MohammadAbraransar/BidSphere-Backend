package com.auction.bidding.controller;

import com.auction.bidding.dto.BidRequest;
import com.auction.bidding.dto.BidMessage;
import com.auction.bidding.model.AuctionItem;
import com.auction.bidding.service.AuctionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/auctions")
@RequiredArgsConstructor
public class AuctionController {

    private final AuctionService auctionService;

    @GetMapping("/items")
    public ResponseEntity<List<AuctionItem>> getActiveItems() {
        return ResponseEntity.ok(auctionService.getActiveItems());
    }

    @PostMapping("/items")
    public ResponseEntity<AuctionItem> createItem(@Valid @RequestBody AuctionItem item, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        item.setCreatorName(principal.getName());
        item.setId(null);
        item.setStatus("PUBLISHED");
        item.setActive(true);
        return ResponseEntity.ok(auctionService.createItem(java.util.Objects.requireNonNull(item)));
    }

    @PostMapping("/bid")
    public ResponseEntity<?> placeBid(@Valid @RequestBody BidRequest request, java.security.Principal principal) {
        try {
            // Set bidder name from authenticated user
            request.setBidderName(principal.getName());
            BidMessage message = auctionService.placeBid(request);
            return ResponseEntity.ok(message);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
