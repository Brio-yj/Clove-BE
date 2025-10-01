package com.example.bemsaseat.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeatPurchaseRequest {
    private List<SeatPurchaseDetail> seats; // 여러 좌석 정보를 담는 리스트
}

