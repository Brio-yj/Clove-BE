package com.example.bemsaseat.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatDetail {
    private String eventName;
    private String section;
    private int seatNumber;
    private int price;
    private String eventTime;
}
