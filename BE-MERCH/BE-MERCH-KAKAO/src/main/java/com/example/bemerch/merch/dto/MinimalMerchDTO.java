package com.example.bemerch.merch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MinimalMerchDTO {
    private String name;
    private int price;
    private String eventName;
    private String email;
    private String tid;
}
