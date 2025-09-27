package com.example.bemsaseat.seat.repository;


import com.example.bemsaseat.seat.model.Seat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByEventNameAndSectionAndReservationStatus(String eventName, String section, String reservationStatus);
    Optional<Seat> findByEventNameAndSectionAndSeatNumberAndEventTimeAndReservationStatus(
            String eventName,
            String section,
            int seatNumber,
            String eventTime,
            String reservationStatus
    );

    Optional<Seat> findByEventNameAndSectionAndSeatNumberAndEventTime(
            String eventName,
            String section,
            int seatNumber,
            String eventTime
    );
    List<Seat> findByEventName(String eventName);

    Optional<Seat> findByEventNameAndSectionAndSeatNumberAndEventTimeAndPrice(
            String eventName,
            String section,
            int seatNumber,
            String eventTime,
            int price
    );

    Optional<Seat> findFirstByEventNameAndSectionAndPriceAndEventTimeAndReservationStatusOrderBySeatNumberAsc(
            String eventName, String section, int price, String eventTime, String reservationStatus);

    Boolean existsByEventNameAndSectionAndPriceAndEventTimeAndReservationStatus
            ( String eventName, String section, int price, String eventTime, String reservationStatus);

}