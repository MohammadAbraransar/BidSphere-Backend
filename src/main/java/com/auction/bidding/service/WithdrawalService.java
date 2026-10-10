package com.auction.bidding.service;

import com.auction.bidding.model.Transaction;
import com.auction.bidding.model.User;
import com.auction.bidding.model.WithdrawalRequest;
import com.auction.bidding.repository.TransactionRepository;
import com.auction.bidding.repository.UserRepository;
import com.auction.bidding.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WithdrawalService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    @Value("${withdrawal.min-amount:50.00}")
    private BigDecimal minWithdrawalAmount;

    @Transactional
    public WithdrawalRequest requestWithdrawal(String username, BigDecimal amount) {
        if (amount == null || amount.compareTo(minWithdrawalAmount) < 0) {
            throw new IllegalArgumentException("Minimum withdrawal amount is " + minWithdrawalAmount);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"VERIFIED".equals(user.getKycStatus())) {
            throw new RuntimeException("Account KYC must be VERIFIED before withdrawing funds.");
        }

        if (user.getWalletBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient wallet balance for this withdrawal request.");
        }

        // Deduct balance to reserve funds
        user.setWalletBalance(user.getWalletBalance().subtract(amount));
        userRepository.save(user);

        // Record withdrawal request
        WithdrawalRequest request = WithdrawalRequest.builder()
                .username(username)
                .amount(amount)
                .status("PENDING")
                .requestTime(LocalDateTime.now())
                .build();
        request = withdrawalRequestRepository.save(request);

        // Record transaction
        Transaction tx = Transaction.builder()
                .username(username)
                .type("WITHDRAWAL")
                .referenceId(request.getId())
                .amount(amount.negate())
                .status("PENDING")
                .timestamp(LocalDateTime.now())
                .description("Funds reserved for withdrawal request #" + request.getId())
                .build();
        transactionRepository.save(tx);

        return request;
    }

    public List<WithdrawalRequest> getMyWithdrawals(String username) {
        return withdrawalRequestRepository.findByUsernameOrderByRequestTimeDesc(username);
    }

    public List<WithdrawalRequest> getPendingWithdrawals() {
        return withdrawalRequestRepository.findByStatusOrderByRequestTimeAsc("PENDING");
    }

    @Transactional
    public WithdrawalRequest processWithdrawal(Long requestId, boolean approve, String adminName, String providerRef, String notes) {
        WithdrawalRequest request = withdrawalRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Withdrawal request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("Request is already processed.");
        }

        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        request.setProcessedTime(LocalDateTime.now());
        request.setProcessedBy(adminName);
        request.setAdminNotes(notes);

        Transaction tx = transactionRepository.findByReferenceIdAndType(request.getId(), "WITHDRAWAL")
                .orElse(null);

        if (approve) {
            if (providerRef == null || providerRef.isBlank()) {
                throw new RuntimeException("Payout provider reference is required for approved withdrawals.");
            }
            request.setStatus("COMPLETED");
            request.setPayoutProviderRef(providerRef);
            
            if (tx != null) {
                tx.setStatus("COMPLETED");
                tx.setDescription("Withdrawal processed via " + providerRef);
                transactionRepository.save(tx);
            }
        } else {
            request.setStatus("REJECTED");
            
            // Refund the user's wallet
            user.setWalletBalance(user.getWalletBalance().add(request.getAmount()));
            userRepository.save(user);

            if (tx != null) {
                tx.setStatus("FAILED");
                tx.setDescription("Withdrawal rejected: " + notes);
                transactionRepository.save(tx);
            }

            // Create refund transaction
            Transaction refund = Transaction.builder()
                    .username(user.getUsername())
                    .type("WITHDRAWAL_REFUND")
                    .referenceId(request.getId())
                    .amount(request.getAmount())
                    .status("COMPLETED")
                    .timestamp(LocalDateTime.now())
                    .description("Refund for rejected withdrawal request #" + request.getId())
                    .build();
            transactionRepository.save(refund);
        }

        return withdrawalRequestRepository.save(request);
    }
}
