package com.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRemoteDto {
    private String id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private Double balance;
    private Boolean banned;
}
