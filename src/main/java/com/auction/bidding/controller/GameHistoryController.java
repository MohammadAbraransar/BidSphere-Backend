package com.auction.bidding.controller;

import com.auction.bidding.repository.GameEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/games")
@RequiredArgsConstructor
public class GameHistoryController {
    private final GameEntryRepository gameEntryRepository;

    @GetMapping("/my-entries")
    public ResponseEntity<?> myEntries(Principal principal) {
        if (principal == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(gameEntryRepository.findByUsernameOrderByTimestampDesc(principal.getName()).stream().map(e -> Map.of(
                "id", e.getId(),
                "roundId", e.getGameRound().getId(),
                "gameType", e.getGameRound().getGameType(),
                "selection", e.getSelection(),
                "outcome", e.getOutcome(),
                "amount", e.getAmount(),
                "timestamp", e.getTimestamp(),
                "result", e.getGameRound().getResult() == null ? "Pending" : e.getGameRound().getResult(),
                "resultNumber", e.getGameRound().getResultNumber() == null ? 0 : e.getGameRound().getResultNumber()
        )).toList());
    }
}
