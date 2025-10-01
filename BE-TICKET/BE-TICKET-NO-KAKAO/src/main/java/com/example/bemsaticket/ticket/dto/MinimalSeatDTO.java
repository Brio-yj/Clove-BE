package com.example.bemsaticket.ticket.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;

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
    private String NAMESPACE;
}
