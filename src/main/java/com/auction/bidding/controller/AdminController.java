package com.auction.bidding.controller;

import com.auction.bidding.model.Bid;
import com.auction.bidding.model.User;
import com.auction.bidding.repository.AuctionItemRepository;
import com.auction.bidding.repository.BidRepository;
import com.auction.bidding.repository.UserRepository;
import com.auction.bidding.repository.TransactionRepository;
import com.auction.bidding.repository.GameRoundRepository;
import com.auction.bidding.repository.GameEntryRepository;
import com.auction.bidding.repository.WithdrawalRequestRepository;
import com.auction.bidding.service.WithdrawalService;
import com.auction.bidding.model.WithdrawalRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserRepository userRepository;
    private final AuctionItemRepository auctionItemRepository;
    private final BidRepository bidRepository;
    private final TransactionRepository transactionRepository;
    private final GameRoundRepository gameRoundRepository;
    private final GameEntryRepository gameEntryRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WithdrawalService withdrawalService;

    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(u -> Map.<String, Object>of(
            "id", u.getId(), "username", u.getUsername(), "email", u.getEmail(),
            "walletBalance", u.getWalletBalance(), "role", u.getRole(),
            "kycStatus", u.getKycStatus(), "level", u.getLevel()
        )).toList());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getSystemStats() {
        long users = userRepository.count();
        long auctions = auctionItemRepository.count();
        long activeAuctions = auctionItemRepository.findByActiveTrue().size();
        long bids = bidRepository.count();
        long transactions = transactionRepository.count();
        long gameRounds = gameRoundRepository.count();
        return ResponseEntity.ok(Map.of(
            "totalUsers", users, "totalItems", auctions, "activeAuctions", activeAuctions,
            "totalBids", bids, "totalTransactions", transactions, "totalGameRounds", gameRounds
        ));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactions() {
        return ResponseEntity.ok(transactionRepository.findAllByOrderByTimestampDesc());
    }

    @GetMapping("/auctions")
    public ResponseEntity<?> getAuctions() {
        return ResponseEntity.ok(auctionItemRepository.findAll().stream().map(a -> Map.of(
            "id", a.getId(), "title", a.getTitle(), "description", a.getDescription() == null ? "" : a.getDescription(),
            "startingPrice", a.getStartingPrice(), "currentPrice", a.getCurrentPrice(),
            "endTime", a.getEndTime(), "active", a.isActive(),
            "creatorName", a.getCreatorName() == null ? "Unknown" : a.getCreatorName(),
            "status", a.getStatus() == null ? "UNKNOWN" : a.getStatus()
        )).toList());
    }

    @GetMapping("/game-rounds")
    public ResponseEntity<?> getGameRounds() {
        return ResponseEntity.ok(gameRoundRepository.findAll().stream().map(r -> Map.of(
            "id", r.getId(), "gameType", r.getGameType(), "status", r.getStatus(),
            "openTime", r.getOpenTime() == null ? "" : r.getOpenTime(),
            "closeTime", r.getCloseTime() == null ? "" : r.getCloseTime(),
            "result", r.getResult() == null ? "Pending" : r.getResult(),
            "resultNumber", r.getResultNumber() == null ? 0 : r.getResultNumber()
        )).toList());
    }

    @GetMapping("/game-entries")
    public ResponseEntity<?> getGameEntries() {
        return ResponseEntity.ok(gameEntryRepository.findAll().stream().map(e -> Map.of(
            "id", e.getId(), "roundId", e.getGameRound().getId(), "gameType", e.getGameRound().getGameType(),
            "username", e.getUsername(), "selection", e.getSelection(), "outcome", e.getOutcome(),
            "amount", e.getAmount(), "timestamp", e.getTimestamp(),
            "result", e.getGameRound().getResult() == null ? "Pending" : e.getGameRound().getResult(),
            "resultNumber", e.getGameRound().getResultNumber() == null ? 0 : e.getGameRound().getResultNumber()
        )).toList());
    }

    @PutMapping("/auctions/{id}/active")
    public ResponseEntity<?> setAuctionActive(@PathVariable Long id, @RequestBody Map<String, Boolean> request) {
        var auction = auctionItemRepository.findById(id).orElse(null);
        if (auction == null) return ResponseEntity.notFound().build();
        boolean active = Boolean.TRUE.equals(request.get("active"));
        if (active && auction.getEndTime().isBefore(java.time.LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("message", "An expired auction cannot be reopened."));
        }
        auction.setActive(active);
        auction.setStatus(active ? "PUBLISHED" : "CANCELLED");
        auctionItemRepository.save(auction);
        return ResponseEntity.ok(Map.of("id", auction.getId(), "active", auction.isActive(), "status", auction.getStatus()));
    }

    @GetMapping("/bids")
    public ResponseEntity<?> getBidHistory() {
        return ResponseEntity.ok(bidRepository.findAllByOrderByBidTimeDesc().stream().map(b -> Map.of(
            "id", b.getId(), "auctionId", b.getItem().getId(), "auctionTitle", b.getItem().getTitle(),
            "bidderName", b.getBidderName(), "amount", b.getBidAmount(), "bidTime", b.getBidTime()
        )).toList());
    }

    @PutMapping("/users/{id}/role")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> request) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if ("ROLE_ADMIN".equals(user.getRole())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Administrator roles cannot be changed from this screen."));
        }
        String requested = String.valueOf(request.getOrDefault("role", "")).trim().toUpperCase();
        if (!requested.equals("USER") && !requested.equals("ROLE_USER")
                && !requested.equals("AUCTION_MAKER") && !requested.equals("ROLE_AUCTION_MAKER")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Role must be USER or AUCTION_MAKER."));
        }
        user.setRole(requested.startsWith("ROLE_") ? requested : "ROLE_" + requested);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("id", user.getId(), "username", user.getUsername(), "role", user.getRole()));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        if ("ROLE_ADMIN".equals(user.getRole())) return ResponseEntity.badRequest().body(Map.of("message", "Administrator accounts cannot be deleted from this screen."));
        userRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
    }

    @PutMapping("/users/{id}/kyc")
    public ResponseEntity<?> updateKycStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();
        String status = request.getOrDefault("status", "").toUpperCase(java.util.Locale.ROOT);
        if (!List.of("PENDING", "VERIFIED", "REJECTED").contains(status)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Status must be PENDING, VERIFIED, or REJECTED."));
        }
        if ("VERIFIED".equals(status) && (user.getFullName() == null || user.getPhoneNumber() == null ||
                user.getDateOfBirth() == null || user.getPostalAddress() == null ||
                user.getBankAccountLast4() == null)) {
            return ResponseEntity.badRequest().body(Map.of("message", "User must complete required profile and payout details before verification."));
        }
        user.setKycStatus(status);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("id", user.getId(), "username", user.getUsername(), "kycStatus", status));
    }

    @GetMapping("/withdrawals")
    public ResponseEntity<?> getWithdrawalRequests() {
        return ResponseEntity.ok(withdrawalRequestRepository.findAll());
    }

    @PutMapping("/withdrawals/{id}/process")
    public ResponseEntity<?> processWithdrawal(@PathVariable Long id, @RequestBody Map<String, Object> payload, java.security.Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            boolean approve = Boolean.TRUE.equals(payload.get("approve"));
            String providerRef = (String) payload.get("providerRef");
            String notes = (String) payload.get("notes");
            WithdrawalRequest req = withdrawalService.processWithdrawal(id, approve, principal.getName(), providerRef, notes);
            return ResponseEntity.ok(req);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
