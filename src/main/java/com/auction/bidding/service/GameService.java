package com.auction.bidding.service;

import com.auction.bidding.dto.GameMessage;
import com.auction.bidding.model.GameEntry;
import com.auction.bidding.model.GameRound;
import com.auction.bidding.model.Transaction;
import com.auction.bidding.model.User;
import com.auction.bidding.repository.GameEntryRepository;
import com.auction.bidding.repository.GameRoundRepository;
import com.auction.bidding.repository.TransactionRepository;
import com.auction.bidding.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GameService {
    private final UserRepository userRepository;
    private final GameRoundRepository gameRoundRepository;
    private final GameEntryRepository gameEntryRepository;
    private final TransactionRepository transactionRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SecureRandom random = new SecureRandom();

    @Transactional
    public void processJoinRequest(GameMessage message) {
        String username = message.getUsername();
        BigDecimal wager = message.getWager();
        String gameType = message.getGameType();
        String selection = message.getPayload() instanceof Map
                ? (String) ((Map<?, ?>) message.getPayload()).get("selection") : null;

        if (username == null || wager == null || wager.compareTo(BigDecimal.ONE) < 0
                || !"SMALL_BIG".equalsIgnoreCase(gameType)
                || selection == null || !(selection.equalsIgnoreCase("SMALL") || selection.equalsIgnoreCase("BIG"))) {
            sendError(username, "Invalid game entry. Choose Small or Big and enter a positive whole-credit stake.");
            return;
        }
        if (wager.stripTrailingZeros().scale() > 0) {
            sendError(username, "Game stakes must use whole virtual credits.");
            return;
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            sendError(username, "Account not found.");
            return;
        }
        if ("ROLE_ADMIN".equals(user.getRole())) {
            sendError(username, "Administrators cannot participate in games.");
            return;
        }
        if (user.getWalletBalance().compareTo(wager) < 0) {
            sendError(username, "Insufficient virtual-credit balance.");
            return;
        }

        int number = random.nextInt(100) + 1;
        String outcome = number <= 50 ? "SMALL" : "BIG";
        boolean won = selection.equalsIgnoreCase(outcome);
        LocalDateTime now = LocalDateTime.now();

        GameRound round = gameRoundRepository.save(GameRound.builder()
                .gameType("SMALL_BIG").status("SETTLED").openTime(now).closeTime(now).result(outcome).resultNumber(number).build());
        GameEntry entry = gameEntryRepository.save(GameEntry.builder()
                .gameRound(round).username(username).amount(wager).selection(selection.toUpperCase())
                .outcome(won ? "WIN" : "LOSE").timestamp(now).build());

        user.setWalletBalance(user.getWalletBalance().subtract(wager));
        BigDecimal payout = won ? wager.multiply(new BigDecimal("1.95")) : BigDecimal.ZERO;
        if (won) user.setWalletBalance(user.getWalletBalance().add(payout));
        userRepository.save(user);

        transactionRepository.save(Transaction.builder().username(username).type("GAME_ENTRY")
                .referenceId(round.getId()).amount(wager.negate()).status("COMPLETED").timestamp(now)
                .description("Small & Big entry #" + entry.getId() + ": selected " + selection.toUpperCase()).build());
        if (won) {
            transactionRepository.save(Transaction.builder().username(username).type("GAME_WIN")
                    .referenceId(round.getId()).amount(payout).status("COMPLETED").timestamp(now)
                    .description("Small & Big payout for round #" + round.getId()).build());
        }

        GameMessage result = GameMessage.builder().type("RESULT").gameType("SMALL_BIG")
                .roomId(round.getId().toString())
                .payload(Map.of("roundId", round.getId(), "entryId", entry.getId(), "outcome", outcome,
                        "won", won, "amount", payout, "number", number, "balance", user.getWalletBalance()))
                .build();
        messagingTemplate.convertAndSendToUser(username, "/queue/games", result);
    }

    public void processGameAction(GameMessage message) {
        // Other game actions are not implemented by this service yet.
    }

    private void sendError(String username, String errorMsg) {
        if (username == null || username.isBlank()) return;
        GameMessage message = GameMessage.builder().type("ERROR").gameType("SMALL_BIG").payload(errorMsg).build();
        messagingTemplate.convertAndSendToUser(username, "/queue/games", message);
    }
}
