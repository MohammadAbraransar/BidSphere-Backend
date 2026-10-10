package com.auction.bidding.controller;

import com.auction.bidding.model.WithdrawalRequest;
import com.auction.bidding.service.WithdrawalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/withdrawals")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping("/request")
    public ResponseEntity<?> requestWithdrawal(@RequestBody Map<String, BigDecimal> payload, Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        try {
            BigDecimal amount = payload.get("amount");
            WithdrawalRequest request = withdrawalService.requestWithdrawal(principal.getName(), amount);
            return ResponseEntity.ok(request);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/my")
    public ResponseEntity<List<WithdrawalRequest>> getMyWithdrawals(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(withdrawalService.getMyWithdrawals(principal.getName()));
    }
}
