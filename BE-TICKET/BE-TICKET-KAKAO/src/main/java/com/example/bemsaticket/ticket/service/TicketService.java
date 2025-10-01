package com.example.bemsaticket.ticket.service;


import com.example.bemsaticket.auth.jwt.TokenProvider;
import com.example.bemsaticket.kakao.KakaoCancelResponse;
import com.example.bemsaticket.kakao.KakaoPayService;
import com.example.bemsaticket.ticket.dto.MinimalSeatDTO;
import com.example.bemsaticket.ticket.dto.TicketResponseDTO;
import com.example.bemsaticket.ticket.model.Ticket;
import com.example.bemsaticket.ticket.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketService {

    private final TokenProvider tokenProvider;
    private final TicketRepository ticketRepository;
    private final RestTemplate restTemplate;
    private final KakaoPayService kakaoPayService;

    @Value("${pay}")
    private Boolean pay;

    public String purchaseTickets(List<MinimalSeatDTO> seatDTOs, String authorizationHeader) {
        log.info("Starting ticket purchase process for {} seats", seatDTOs.size());
        try {
            String token = authorizationHeader.substring(7);
            String email = tokenProvider.getEmailFromToken(token);
            Long userId = tokenProvider.getMemberIdFromToken(token);
            log.debug("User email: {}, User ID: {}", email, userId);

            for (MinimalSeatDTO seatDTO : seatDTOs) {
                log.debug("Processing seat: {}", seatDTO);
                Ticket ticket = new Ticket();
                ticket.setEventName(seatDTO.getEventName());
                ticket.setSection(seatDTO.getSection());
                ticket.setSeatNumber(seatDTO.getSeatNumber());
                ticket.setPrice(seatDTO.getPrice());
                ticket.setEventTime(seatDTO.getEventTime());
                ticket.setPurchaseDate(seatDTO.getPurchaseDate());
                ticket.setPurchaseTime(seatDTO.getPurchaseTime());
                ticket.setTid(seatDTO.getTid());
                ticket.setNAMESPACE(seatDTO.getNAMESPACE());
                ticket.setUserEmail(email);
                ticket.setUserId(userId);
                ticketRepository.save(ticket);
                log.debug("Ticket saved: {}", ticket);
            }
            log.info("Ticket purchase process completed successfully");
            return "티켓이 성공적으로 저장되었습니다.";
        } catch (Exception e) {
            log.error("Error occurred during ticket purchase process", e);
            throw e;
        }
    }

    public String cancelTicket(List<MinimalSeatDTO> seatDTOs, String authorizationHeader) {
        log.info("Starting ticket cancellation process for seats: {}", seatDTOs);
        try {
            String token = authorizationHeader.substring(7);
            String email = tokenProvider.getEmailFromToken(token);
            log.debug("User email: {}", email);

            int totalCancelAmount = 0;

            for (MinimalSeatDTO seatDTO : seatDTOs) {
                log.info("Processing cancellation for seat: {}", seatDTO);
                Ticket existingTicket = ticketRepository.findBySeatNumberAndEventNameAndSectionAndUserEmailAndEventTime(
                        seatDTO.getSeatNumber(),
                        seatDTO.getEventName(),
                        seatDTO.getSection(),
                        email,
                        seatDTO.getEventTime()
                );

                if (existingTicket == null) {
                    log.warn("Ticket not found for cancellation: {}", seatDTO);
                    throw new IllegalArgumentException("해당 티켓이 존재하지 않습니다.");
                }

                totalCancelAmount += seatDTO.getPrice();

                log.info("Sending cancel request to seat service");
                sendCancelRequestToSeatService(seatDTO, authorizationHeader);

                log.info("Deleting ticket from repository");
                ticketRepository.delete(existingTicket);
            }

            if (pay) {
                log.info("Initiating Kakao Pay refund process with total amount: {}", totalCancelAmount);

                // Assuming all tickets have the same TID, this is a simple example.
                String tid = seatDTOs.get(0).getTid();

                KakaoCancelResponse cancelResponse = kakaoPayService.kakaoCancel(tid, totalCancelAmount, 0, 0);

                if (cancelResponse == null || cancelResponse.getTid() == null) {
                    log.error("Kakao Pay refund request failed");
                    throw new IllegalStateException("카카오페이 환불 요청에 실패했습니다.");
                }
                log.info("Kakao Pay refund completed successfully");
            }

            log.info("Ticket cancellation process completed successfully");
            return "티켓이 성공적으로 취소되었습니다.";
        } catch (Exception e) {
            log.error("Error occurred during ticket cancellation process", e);
            throw e;
        }
    }

    private void sendCancelRequestToSeatService(MinimalSeatDTO seatDTO, String authorizationHeader) {
        log.info("Sending cancel request to seat service for seat: {}", seatDTO);
        String NAMESPACE = seatDTO.getNAMESPACE();
        log.info("NAMESPACE-----------------:{}",NAMESPACE);
//        String url = "http://localhost:8082/seat/cancel";
        String url = String.format("http://cse.ticketclove.com/%s/seat/cancel", NAMESPACE);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", authorizationHeader);

        HttpEntity<MinimalSeatDTO> requestEntity = new HttpEntity<>(seatDTO, headers);

        try {
            restTemplate.put(url, requestEntity);
            log.info("Cancel request sent successfully to seat service");
        } catch (Exception e) {
            log.error("Error occurred while sending cancel request to seat service", e);
            throw e;
        }
    }

    public TicketResponseDTO getAllTickets(String authorizationHeader) {
        log.info("Fetching all tickets");
        try {
            List<Ticket> tickets = ticketRepository.findAll();
            long totalTicketCount = ticketRepository.count();
            log.info("Total tickets fetched: {}", totalTicketCount);
            return new TicketResponseDTO(tickets, totalTicketCount);
        } catch (Exception e) {
            log.error("Error occurred while fetching all tickets", e);
            throw e;
        }
    }

    public List<Ticket> getTicketsByEventName(String eventName, String authorizationHeader) {
        log.info("Fetching tickets for event: {}", eventName);
        try {
            String token = authorizationHeader.substring(7);
            String email = tokenProvider.getEmailFromToken(token);
            Long userId = tokenProvider.getMemberIdFromToken(token);
            log.debug("User email: {}, User ID: {}", email, userId);

            List<Ticket> tickets = ticketRepository.findByEventNameAndUserEmailAndUserId(eventName, email, userId);
            log.info("Fetched {} tickets for event: {}", tickets.size(), eventName);
            return tickets;
        } catch (Exception e) {
            log.error("Error occurred while fetching tickets for event: {}", eventName, e);
            throw e;
        }
    }

    public List<Ticket> getAllTicketsForUser(String authorizationHeader) {
        log.info("Fetching all tickets for user");
        try {
            String token = authorizationHeader.substring(7);
            String userEmail = tokenProvider.getEmailFromToken(token);
            log.debug("User email: {}", userEmail);

            List<Ticket> tickets = ticketRepository.findByUserEmail(userEmail);
            log.info("Fetched {} tickets for user: {}", tickets.size(), userEmail);
            return tickets;
        } catch (Exception e) {
            log.error("Error occurred while fetching all tickets for user", e);
            throw e;
        }
    }

}