package com.auction.bidding.service;

import com.auction.bidding.model.Transaction;
import com.auction.bidding.model.User;
import com.auction.bidding.model.WithdrawalRequest;
import com.auction.bidding.repository.TransactionRepository;
import com.auction.bidding.repository.UserRepository;
import com.auction.bidding.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class WithdrawalServiceTest {

    @Mock
    private WithdrawalRequestRepository withdrawalRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private WithdrawalService withdrawalService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(withdrawalService, "minWithdrawalAmount", new BigDecimal("50.00"));
    }

    @Test
    void testRequestWithdrawal_Success() {
        User user = new User();
        user.setUsername("john");
        user.setWalletBalance(new BigDecimal("200.00"));
        user.setKycStatus("VERIFIED");
        user.setBankAccountLast4("1234");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(withdrawalRepository.save(any())).thenAnswer(invocation -> {
            WithdrawalRequest req = invocation.getArgument(0);
            req.setId(1L);
            return req;
        });

        WithdrawalRequest req = withdrawalService.requestWithdrawal("john", new BigDecimal("50.00"));

        assertEquals("PENDING", req.getStatus());
        assertEquals(new BigDecimal("50.00"), req.getAmount());
        assertEquals(new BigDecimal("150.00"), user.getWalletBalance());
        
        verify(userRepository).save(user);
        verify(transactionRepository).save(argThat(tx -> 
            tx.getType().equals("WITHDRAWAL") && 
            tx.getAmount().compareTo(new BigDecimal("-50.00")) == 0 &&
            tx.getStatus().equals("PENDING")
        ));
    }

    @Test
    void testRequestWithdrawal_InsufficientBalance() {
        User user = new User();
        user.setUsername("john");
        user.setWalletBalance(new BigDecimal("40.00"));
        user.setKycStatus("VERIFIED");
        user.setBankAccountLast4("1234");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            withdrawalService.requestWithdrawal("john", new BigDecimal("50.00"));
        });

        assertTrue(exception.getMessage().contains("Insufficient wallet balance"));
    }

    @Test
    void testRequestWithdrawal_KycNotVerified() {
        User user = new User();
        user.setUsername("john");
        user.setWalletBalance(new BigDecimal("200.00"));
        user.setKycStatus("PENDING");

        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));

        Exception exception = assertThrows(RuntimeException.class, () -> {
            withdrawalService.requestWithdrawal("john", new BigDecimal("50.00"));
        });

        assertTrue(exception.getMessage().contains("Account KYC must be VERIFIED"));
    }

    @Test
    void testProcessWithdrawal_Approve() {
        WithdrawalRequest req = new WithdrawalRequest();
        req.setId(1L);
        req.setUsername("john");
        req.setAmount(new BigDecimal("50.00"));
        req.setStatus("PENDING");

        Transaction tx = new Transaction();
        tx.setId(10L);
        tx.setType("WITHDRAWAL");
        tx.setStatus("PENDING");
        tx.setReferenceId(1L);

        User user = new User();
        user.setUsername("john");
        user.setWalletBalance(new BigDecimal("150.00"));

        when(withdrawalRepository.findById(1L)).thenReturn(Optional.of(req));
        when(transactionRepository.findByReferenceIdAndType(1L, "WITHDRAWAL")).thenReturn(Optional.of(tx));
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(withdrawalRepository.save(any())).thenReturn(req);

        WithdrawalRequest processed = withdrawalService.processWithdrawal(1L, true, "admin", "stripe_123", "Approved payout");

        assertEquals("COMPLETED", processed.getStatus());
        assertEquals("stripe_123", processed.getPayoutProviderRef());
        assertEquals("COMPLETED", tx.getStatus());

        verify(withdrawalRepository).save(processed);
        verify(transactionRepository).save(tx);
    }

    @Test
    void testProcessWithdrawal_Reject() {
        WithdrawalRequest req = new WithdrawalRequest();
        req.setId(1L);
        req.setUsername("john");
        req.setAmount(new BigDecimal("50.00"));
        req.setStatus("PENDING");

        Transaction tx = new Transaction();
        tx.setId(10L);
        tx.setType("WITHDRAWAL");
        tx.setStatus("PENDING");
        tx.setReferenceId(1L);

        User user = new User();
        user.setUsername("john");
        user.setWalletBalance(new BigDecimal("150.00"));

        when(withdrawalRepository.findById(1L)).thenReturn(Optional.of(req));
        when(transactionRepository.findByReferenceIdAndType(1L, "WITHDRAWAL")).thenReturn(Optional.of(tx));
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(user));
        when(withdrawalRepository.save(any())).thenReturn(req);

        WithdrawalRequest processed = withdrawalService.processWithdrawal(1L, false, "admin", null, "Violation of terms");

        assertEquals("REJECTED", processed.getStatus());
        assertEquals("FAILED", tx.getStatus());
        assertEquals(new BigDecimal("200.00"), user.getWalletBalance());

        verify(withdrawalRepository).save(processed);
        verify(transactionRepository).save(tx);
        verify(userRepository).save(user);
        
        verify(transactionRepository).save(argThat(t -> 
            t.getType().equals("WITHDRAWAL_REFUND") && 
            t.getAmount().compareTo(new BigDecimal("50.00")) == 0 &&
            t.getReferenceId().equals(1L)
        ));
    }
}
