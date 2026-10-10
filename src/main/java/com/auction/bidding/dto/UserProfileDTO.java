package com.auction.bidding.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileDTO {
    private Long id;
    private String username;
    private String email;
    private String phoneNumber;
    private String fullName;
    private String dateOfBirth;
    private String postalAddress;
    private String bankAccountName;
    private String bankAccountLast4;
    private BigDecimal walletBalance;
    private String role;
    private String kycStatus;
    private Integer level;
    private Integer xp;
}
