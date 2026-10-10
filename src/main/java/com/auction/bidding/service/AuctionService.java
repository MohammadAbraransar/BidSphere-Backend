package com.auction.bidding.service;

import com.auction.bidding.dto.BidRequest;
import com.auction.bidding.dto.BidMessage;
import com.auction.bidding.model.AuctionItem;
import com.auction.bidding.model.Bid;
import com.auction.bidding.repository.AuctionItemRepository;
import com.auction.bidding.repository.BidRepository;
import com.auction.bidding.repository.UserRepository;
import com.auction.bidding.repository.TransactionRepository;
import com.auction.bidding.model.User;
import com.auction.bidding.model.Transaction;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuctionService {

    private final AuctionItemRepository itemRepository;
    private final BidRepository bidRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<AuctionItem> getActiveItems() {
        return itemRepository.findByActiveTrue();
    }

    public AuctionItem createItem(@NonNull AuctionItem item) {
        if (item.getTitle() == null || item.getTitle().isBlank()) {
            throw new IllegalArgumentException("Auction title is required.");
        }
        if (item.getStartingPrice() == null || item.getStartingPrice().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Starting price must be greater than zero.");
        }
        if (item.getEndTime() == null || !item.getEndTime().isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Auction end time must be in the future.");
        }
        item.setCurrentPrice(item.getStartingPrice());
        item.setStatus("PUBLISHED");
        item.setActive(true);
        return itemRepository.save(item);
    }

    @Transactional
    public BidMessage placeBid(@NonNull BidRequest request) {
        if (request.getItemId() == null) throw new IllegalArgumentException("Auction ID is required.");
        if (request.getAmount() == null || request.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Bid amount must be greater than zero.");
        }
        if (request.getBidderName() == null || request.getBidderName().isBlank()) {
            throw new IllegalArgumentException("Authenticated bidder is required.");
        }
        Long itemId = request.getItemId();
        AuctionItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!item.isActive()) {
            throw new RuntimeException("Auction is closed for this item");
        }

        if (item.getEndTime().isBefore(LocalDateTime.now())) {
            item.setActive(false);
            itemRepository.save(item);
            throw new RuntimeException("Auction has ended");
        }

        if (request.getAmount().compareTo(item.getCurrentPrice()) <= 0) {
            throw new RuntimeException("Bid amount must be higher than current price");
        }
        
        // Prevent ADMIN from bidding
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new RuntimeException("Administrators are not allowed to place bids.");
        }

        User bidder = userRepository.findByUsername(request.getBidderName())
                .orElseThrow(() -> new RuntimeException("Bidder not found"));
        if (bidder.getWalletBalance().compareTo(request.getAmount()) < 0) {
            throw new RuntimeException("Insufficient virtual credits to place this bid");
        }

        // Refund previous highest bidder
        List<Bid> bids = bidRepository.findByItemIdOrderByBidAmountDesc(item.getId());
        if (!bids.isEmpty()) {
            Bid highestBid = bids.get(0);
            User prevBidder = userRepository.findByUsername(highestBid.getBidderName()).orElse(null);
            if (prevBidder != null) {
                prevBidder.setWalletBalance(prevBidder.getWalletBalance().add(highestBid.getBidAmount()));
                userRepository.save(prevBidder);
                
                Transaction refundTx = Transaction.builder()
                        .username(prevBidder.getUsername())
                        .type("BID_REFUND")
                        .referenceId(item.getId())
                        .amount(highestBid.getBidAmount())
                        .status("COMPLETED")
                        .timestamp(LocalDateTime.now())
                        .description("Refund for outbid on auction #" + item.getId())
                        .build();
                transactionRepository.save(refundTx);
            }
        }

        // Deduct from new bidder
        bidder.setWalletBalance(bidder.getWalletBalance().subtract(request.getAmount()));
        userRepository.save(bidder);

        Transaction bidTx = Transaction.builder()
                .username(bidder.getUsername())
                .type("BID_PLACED")
                .referenceId(item.getId())
                .amount(request.getAmount().negate())
                .status("COMPLETED")
                .timestamp(LocalDateTime.now())
                .description("Placed bid on auction #" + item.getId())
                .build();
        transactionRepository.save(bidTx);

        // Update item price
        item.setCurrentPrice(request.getAmount());
        itemRepository.save(item); // Optimistic locking will prevent race conditions here

        // Save bid history
        Bid bid = Bid.builder()
                .item(item)
                .bidderName(request.getBidderName())
                .bidAmount(request.getAmount())
                .bidTime(LocalDateTime.now())
                .build();
        bidRepository.save(java.util.Objects.requireNonNull(bid));

        // Broadcast new bid to all subscribed clients
        BidMessage message = BidMessage.builder()
                .itemId(item.getId())
                .bidderName(request.getBidderName())
                .currentPrice(request.getAmount())
                .bidTime(bid.getBidTime())
                .message("New bid placed by " + request.getBidderName())
                .build();

        messagingTemplate.convertAndSend("/topic/bids/" + item.getId(), java.util.Objects.requireNonNull(message));

        return message;
    }
}
