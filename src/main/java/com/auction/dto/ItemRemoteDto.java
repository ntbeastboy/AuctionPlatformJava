package com.auction.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ItemRemoteDto {
    private String id;
    private String name;
    private String description;
    private String category;
    private Double startPrice;
    private Double currentPrice;
    private String sellerId;
    private String sellerName;
    private String status;
}
