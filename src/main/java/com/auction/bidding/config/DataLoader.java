package com.auction.bidding.config;

import com.auction.bidding.model.User;
import com.auction.bidding.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public void run(String... args) throws Exception {
        upsertUser("admin", "admin123", "admin@test.com", "ROLE_ADMIN", new BigDecimal("10000.00"));
        upsertUser("user", "user123", "user@test.com", "ROLE_USER", new BigDecimal("5000.00"));
        upsertUser("auctionmaker", "maker123", "maker@test.com", "ROLE_AUCTION_MAKER", new BigDecimal("5000.00"));
        upsertUser("demo.admin", "admin123", "demo.admin@test.com", "ROLE_ADMIN", new BigDecimal("10000.00"));
        upsertUser("demo.auction", "maker123", "demo.auction@test.com", "ROLE_AUCTION_MAKER", new BigDecimal("5000.00"));
        upsertUser("demo.user", "user123", "demo.user@test.com", "ROLE_USER", new BigDecimal("5000.00"));
    }

    private void upsertUser(String username, String rawPassword, String email, String role, BigDecimal wallet) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(rawPassword))
                    .email(email)
                    .role(role)
                    .walletBalance(wallet)
                    .kycStatus("VERIFIED")
                    .build();
        } else {
            user.setPassword(passwordEncoder.encode(rawPassword));
            user.setRole(role);
            if (user.getWalletBalance() == null || user.getWalletBalance().compareTo(BigDecimal.ZERO) <= 0) {
                user.setWalletBalance(wallet);
            }
            if (user.getKycStatus() == null || user.getKycStatus().equals("PENDING")) {
                user.setKycStatus("VERIFIED");
            }
        }
        userRepository.save(user);
    }
}
