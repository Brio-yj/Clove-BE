package com.example.bemsaseat.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatPurchaseDetail {
    private String eventName;
    private String section;
    private int price;
    private String eventTime;
}
