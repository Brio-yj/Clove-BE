package com.example.bemsaseat.seat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatReservationResponseDTO {
    private String eventName;       // 이벤트 이름
    private String section;         // 섹션 이름
    private int seatNumber;         // 좌석 번호
    private int price;              // 가격
    private String eventTime;       // 이벤트 시간
    private LocalDate purchaseDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime purchaseTime;
    private String tid;
}