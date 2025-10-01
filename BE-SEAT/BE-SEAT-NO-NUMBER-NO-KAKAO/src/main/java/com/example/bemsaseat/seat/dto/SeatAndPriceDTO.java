package com.example.bemsaseat.seat.dto;

import jakarta.persistence.ElementCollection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatAndPriceDTO {
    private String section;
    private int price;
    private int count;
    private String eventName;
    @ElementCollection
    private List<String> eventTime;
}
