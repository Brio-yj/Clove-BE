package com.example.msaeventinformation.repository;

import com.example.msaeventinformation.model.SeatAndPrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeatAndPriceRepository extends JpaRepository<SeatAndPrice, Long> {
    // 필요에 따라 메소드 추가
}