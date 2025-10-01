package com.example.msaeventinformation.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchDTO {
    private String name;
    private int price;
    private int count;
    private String image;
    private String eventName;
}
