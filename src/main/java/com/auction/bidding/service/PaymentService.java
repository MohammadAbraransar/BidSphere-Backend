package com.auction.bidding.service;

import com.auction.bidding.dto.PaymentRequest;
import com.auction.bidding.dto.PaymentResponse;
import com.auction.bidding.model.User;
import com.auction.bidding.repository.UserRepository;
import com.auction.bidding.repository.TransactionRepository;
import com.auction.bidding.model.Transaction;
import java.time.LocalDateTime;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentService {

    @Value("${stripe.api.key}")
    private String stripeApiKey;

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeApiKey;
    }

    public PaymentResponse createPaymentIntent(PaymentRequest request, String username) throws StripeException {
        // Amount must be in cents for Stripe (e.g., $10.00 is 1000)
        long amountInCents = request.getAmount().multiply(new BigDecimal("100")).longValue();

        PaymentIntentCreateParams params =
                PaymentIntentCreateParams.builder()
                        .setAmount(amountInCents)
                        .setCurrency(request.getCurrency())
                        .putMetadata("username", username)
                        .build();

        PaymentIntent paymentIntent = PaymentIntent.create(params);

        return PaymentResponse.builder()
                .clientSecret(paymentIntent.getClientSecret())
                .paymentIntentId(paymentIntent.getId())
                .status(paymentIntent.getStatus())
                .build();
    }

    @Transactional
    public void confirmPaymentAndAddFunds(String paymentIntentId, String username) throws StripeException {
        PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);
        
        // In a real app, you should use Stripe Webhooks to listen for "payment_intent.succeeded" 
        // to prevent users from calling this endpoint maliciously. 
        // For MVP, we verify the status directly via API call.
        if ("succeeded".equals(paymentIntent.getStatus())) {
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            BigDecimal amountToAdd = new BigDecimal(paymentIntent.getAmount()).divide(new BigDecimal("100"));
            user.setWalletBalance(user.getWalletBalance().add(amountToAdd));
            userRepository.save(user);
        } else {
            throw new RuntimeException("Payment has not succeeded yet. Current status: " + paymentIntent.getStatus());
        }
    }
}
