package com.example.bemerch.merch.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchPurchaseDetail {
    private String name;
    private int price;
    private String eventName;
}
