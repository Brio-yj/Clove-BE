package com.example.bemerch.merch.dto.refund;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchRefundDetail {
    private String name;
    private int price;
    private String mid;
    private String eventName;
    private String tid;
}
