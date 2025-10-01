package com.example.bemsaseat.seat.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@AllArgsConstructor
public class MinimalSeatDTO {
    private String eventName;
    private String section;
    private int seatNumber;
    private int price;
    private String eventTime;
    private LocalDate purchaseDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime purchaseTime;
    private String tid;
    @Value("${NAMESPACE}")
    private String NAMESPACE;
}
