package com.auction.bidding.controller;

import com.auction.bidding.dto.PaymentRequest;
import com.auction.bidding.dto.PaymentResponse;
import com.auction.bidding.service.PaymentService;
import com.stripe.exception.StripeException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @Value("${stripe.webhook.secret:whsec_test}")
    private String endpointSecret;

    @PostMapping("/create-intent")
    public ResponseEntity<?> createPaymentIntent(@Valid @RequestBody PaymentRequest request, Principal principal) {
        try {
            PaymentResponse response = paymentService.createPaymentIntent(request, principal.getName());
            return ResponseEntity.ok(response);
        } catch (StripeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/confirm/{paymentIntentId}")
    public ResponseEntity<?> confirmPayment(@PathVariable String paymentIntentId, Principal principal) {
        try {
            paymentService.confirmPaymentAndAddFunds(paymentIntentId, principal.getName());
            return ResponseEntity.ok(Map.of("message", "Payment confirmed and wallet updated successfully!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<?> handleStripeWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, endpointSecret);
            if ("payment_intent.succeeded".equals(event.getType())) {
                // Verified provider callback for payment intent
                // Ideally we'd extract the payment intent ID and process the wallet update here
                // For simplicity, we just return OK since the frontend confirm payment handles the logic for this demo.
            } else if ("payout.paid".equals(event.getType())) {
                // Handle payout success
            }
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Webhook verification failed: " + e.getMessage());
        }
    }

}
