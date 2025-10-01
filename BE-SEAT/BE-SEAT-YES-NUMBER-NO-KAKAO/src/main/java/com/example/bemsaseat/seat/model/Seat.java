package com.example.bemsaseat.seat.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String eventName;
    private String section;
    private int seatNumber;
    private int price;
    private String eventTime;
    private String reservationStatus; // Typically "NO" or "YES"

    public Seat(String eventName, String section, int seatNumber, int price, String eventTime) {
        this.eventName = eventName;
        this.section = section;
        this.seatNumber = seatNumber;
        this.price = price;
        this.eventTime = eventTime;
        this.reservationStatus = "NO"; // Initial status
    }
}