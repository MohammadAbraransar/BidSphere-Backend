package com.auction.bidding.controller;

import com.auction.bidding.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionRepository transactionRepository;

    @GetMapping("/my")
    public ResponseEntity<?> myTransactions(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(transactionRepository.findByUsernameOrderByTimestampDesc(principal.getName()).stream().map(t -> Map.of(
                "id", t.getId(), "type", t.getType(), "referenceId", t.getReferenceId() == null ? 0L : t.getReferenceId(),
                "amount", t.getAmount(), "status", t.getStatus(), "timestamp", t.getTimestamp(),
                "description", t.getDescription() == null ? "" : t.getDescription()
        )).toList());
    }
}
