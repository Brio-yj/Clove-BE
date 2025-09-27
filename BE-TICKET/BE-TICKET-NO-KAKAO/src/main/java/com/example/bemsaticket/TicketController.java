package com.example.bemsaticket;


import com.example.bemsaticket.ticket.dto.MinimalSeatDTO;
import com.example.bemsaticket.ticket.dto.TicketResponseDTO;
import com.example.bemsaticket.ticket.model.Ticket;
import com.example.bemsaticket.ticket.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/ticket")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @PostMapping("/purchase")
    public ResponseEntity<String> purchaseTickets(@RequestBody List<MinimalSeatDTO> seatDTOs,
                                                  @RequestHeader("Authorization") String authorizationHeader) {
        log.info("Received request to purchase {} tickets", seatDTOs.size());
        try {
            String resultMessage = ticketService.purchaseTickets(seatDTOs, authorizationHeader);
            log.info("Ticket purchase successful");
            return ResponseEntity.status(HttpStatus.CREATED).body(resultMessage);
        } catch (Exception e) {
            log.error("Error occurred during ticket purchase", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("티켓 구매 처리 중 오류 발생");
        }
    }

    @DeleteMapping("/refund")
    public ResponseEntity<String> cancelTickets(@RequestBody List<MinimalSeatDTO> seatDTOs,
                                                @RequestHeader("Authorization") String authorizationHeader) {
        log.info("Received request to cancel tickets: {}", seatDTOs);
        try {
            String resultMessage = ticketService.cancelTicket(seatDTOs, authorizationHeader);
            log.info("Ticket cancellations successful");
            return ResponseEntity.ok(resultMessage);
        } catch (Exception e) {
            log.error("Error occurred during ticket cancellation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("티켓 취소 처리 중 오류 발생");
        }
    }



    @GetMapping("/my/{eventName}")
    public ResponseEntity<List<Ticket>> getTicketsByEventName(@PathVariable String eventName,
                                                              @RequestHeader("Authorization") String authorizationHeader) {
        log.info("Received request to get tickets for event: {}", eventName);
        try {
            List<Ticket> tickets = ticketService.getTicketsByEventName(eventName, authorizationHeader);
            log.info("Successfully retrieved {} tickets for event: {}", tickets.size(), eventName);
            return ResponseEntity.ok(tickets);
        } catch (Exception e) {
            log.error("Error occurred while retrieving tickets for event: {}", eventName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/my/all")
    public ResponseEntity<List<Ticket>> getAllTicketsForUser(@RequestHeader("Authorization") String authorizationHeader) {
        log.info("Received request to get all tickets for user");
        try {
            List<Ticket> tickets = ticketService.getAllTicketsForUser(authorizationHeader);
            log.info("Successfully retrieved {} tickets for user", tickets.size());
            return ResponseEntity.ok(tickets);
        } catch (Exception e) {
            log.error("Error occurred while retrieving all tickets for user", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @GetMapping("/all")
    public ResponseEntity<TicketResponseDTO> getAllTickets(@RequestHeader("Authorization") String authorizationHeader) {
        log.info("Received request to get all tickets");
        try {
            TicketResponseDTO responseDTO = ticketService.getAllTickets(authorizationHeader);
            log.info("Successfully retrieved all tickets. Total count: {}", responseDTO.getTotalTicketCount());
            return ResponseEntity.ok(responseDTO);
        } catch (Exception e) {
            log.error("Error occurred while retrieving all tickets", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}