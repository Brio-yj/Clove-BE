package com.example.bemsaticket.ticket.repository;


import com.example.bemsaticket.ticket.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketRepository extends JpaRepository<Ticket, Long> {
    Ticket findBySeatNumberAndEventNameAndSectionAndUserEmailAndEventTime(
            int seatNumber,
            String eventName,
            String section,
            String userEmail,
            String eventTime
    );

    List<Ticket> findByUserEmailAndUserId(String userEmail, Long userId);
    List<Ticket> findByEventNameAndUserEmailAndUserId(String eventName, String userEmail, Long userId);
    List<Ticket> findByUserEmail(String userEmail);

}