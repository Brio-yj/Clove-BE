package com.example.bemsaseat.seat.service;


import com.example.bemsaseat.kakao.KakaoPayService;
import com.example.bemsaseat.kakao.dto.KakaoReadyResponse;
import com.example.bemsaseat.kakao.dto.PurchaseResponseDTO;
import com.example.bemsaseat.seat.config.RandomStringGenerator;
import com.example.bemsaseat.seat.dto.*;
import com.example.bemsaseat.seat.model.Seat;
import com.example.bemsaseat.seat.repository.SeatRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SeatService {

    private final SeatRepository seatRepository;
    private final KakaoPayService kakaoPayService;
    private final RestTemplate restTemplate;
    private static final Logger logger = LoggerFactory.getLogger(SeatService.class);

    @Value("${NAMESPACE}")
    private String NAMESPACE;

    @Value("${pay}")
    private Boolean pay;

    public PurchaseResponseDTO purchaseSeats(SeatPurchaseRequest seatRequest, String email, String jwtToken) {
        log.info("Starting purchaseSeats process for email: {}", email);

        List<MinimalSeatDTO> minimalSeatDTOList = new ArrayList<>();

        for (SeatPurchaseDetail seatPurchaseDetail : seatRequest.getSeats()) {
            Boolean exists = seatRepository.existsByEventNameAndSectionAndPriceAndEventTimeAndReservationStatus(
                    seatPurchaseDetail.getEventName(),
                    seatPurchaseDetail.getSection(),
                    seatPurchaseDetail.getPrice(),
                    seatPurchaseDetail.getEventTime(),
                    "NO"
            );

            if (!exists) {
                log.error("Seat not available: EventName={}, Section={}, Price={}, EventTime={}",
                        seatPurchaseDetail.getEventName(), seatPurchaseDetail.getSection(), seatPurchaseDetail.getPrice(), seatPurchaseDetail.getEventTime());
                throw new IllegalArgumentException("이미 구매된 자리입니다.");
            }

            // MinimalSeatDTO 생성 및 추가, 일단 여기서 0으로 좌석 번호 초기화
            MinimalSeatDTO minimalSeatDTO = new MinimalSeatDTO(
                    seatPurchaseDetail.getEventName(),
                    seatPurchaseDetail.getSection(),
                    seatPurchaseDetail.getPrice(),
                    seatPurchaseDetail.getEventTime(),
                    0,  // seatNumber 초기화
                    LocalDate.now(),
                    LocalTime.now(),
                    "",  // 이후에 TID를 설정할 것이므로 초기화 상태로 남겨둔 부분
                    NAMESPACE
            );

            minimalSeatDTOList.add(minimalSeatDTO);
        }

        if (pay) {
            log.info("All seats validated, preparing KakaoPay payment...");
            KakaoReadyResponse readyResponse = kakaoPayService.kakaoPayReady(seatRequest.getSeats(), email);

            String tid = readyResponse.getTid();
            log.info("KakaoPay payment ready response with TID: {}, creating PurchaseResponseDTO...", tid);

            // 각 DTO에 TID 설정
            for (MinimalSeatDTO seatDTO : minimalSeatDTOList) {
                seatDTO.setTid(tid);
            }

            return new PurchaseResponseDTO(readyResponse, email, minimalSeatDTOList.size(), jwtToken, minimalSeatDTOList);

        } else {
            log.info("Processing without Kakao...");

            // 각 DTO에 랜덤 16자리 문자열 설정
            for (MinimalSeatDTO seatDTO : minimalSeatDTOList) {
                seatDTO.setTid(RandomStringGenerator.generateRandomString(16));
            }

            // 좌석 정보와 함께 예약 요청 전송
            log.info("Sending purchase request for seat reservation...");
            sendPurchaseRequest(minimalSeatDTOList, jwtToken);

            PurchaseResponseDTO purchaseResponse = new PurchaseResponseDTO(minimalSeatDTOList, email, jwtToken);

            log.info("Returning booked seats information: {}", purchaseResponse);
            return purchaseResponse;
        }
    }


    public void sendPurchaseRequest(List<MinimalSeatDTO> minimalSeatDTOs, String jwtToken) {
        log.debug("Starting sendPurchaseRequest with {} seats", minimalSeatDTOs.size());

        List<MinimalSeatDTO> minimalSeatDTOListWithUpdatedNumbers = new ArrayList<>();
        List<Seat> updatedSeats = new ArrayList<>();

        try {
            for (MinimalSeatDTO seatDTO : minimalSeatDTOs) {
                Optional<Seat> optionalSeat = seatRepository.findFirstByEventNameAndSectionAndPriceAndEventTimeAndReservationStatusOrderBySeatNumberAsc(
                        seatDTO.getEventName(),
                        seatDTO.getSection(),
                        seatDTO.getPrice(),
                        seatDTO.getEventTime(),
                        "NO"
                );

                if (!optionalSeat.isPresent()) {
                    log.error("No available seat found for event: {}, section: {}, price: {}, eventTime: {}",
                            seatDTO.getEventName(), seatDTO.getSection(), seatDTO.getPrice(), seatDTO.getEventTime());
                    throw new IllegalStateException("요청한 좌석을 찾을 수 없습니다.");
                }

                Seat foundSeat = optionalSeat.get();
                int allocatedSeatNumber = foundSeat.getSeatNumber();

                // DTO에 좌석 번호 설정 및 새로운 리스트에 추가
                MinimalSeatDTO updatedSeatDTO = new MinimalSeatDTO(
                        seatDTO.getEventName(),
                        seatDTO.getSection(),
                        seatDTO.getPrice(),
                        seatDTO.getEventTime(),
                        allocatedSeatNumber,
                        seatDTO.getPurchaseDate(),
                        seatDTO.getPurchaseTime(),
                        seatDTO.getTid(),
                        seatDTO.getNAMESPACE()
                );

                minimalSeatDTOListWithUpdatedNumbers.add(updatedSeatDTO);

                // 좌석의 상태를 "YES"로 설정
                foundSeat.setReservationStatus("YES");
                seatRepository.save(foundSeat);
                updatedSeats.add(foundSeat);

                log.info("Seat reserved: {} -> SeatNumber: {}", updatedSeatDTO, allocatedSeatNumber);
            }

            // HTTP 요청을 위한 준비
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (jwtToken.startsWith("Bearer ")) {
                jwtToken = jwtToken.substring(7);
            }

            headers.setBearerAuth(jwtToken);
            log.info("Headers being sent: {}", headers);

            HttpEntity<List<MinimalSeatDTO>> request = new HttpEntity<>(minimalSeatDTOListWithUpdatedNumbers, headers);
            log.info("Request body: {}", request.getBody());

            String url= String.format("http://cse.ticketclove.com/%s/ticket/purchase", NAMESPACE);
//            String url = "http://cse.ticketclove.com/default/ticket/purchase";
//        String url = String.format("http://localhost:8083/ticket/purchase");
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Failed to complete purchase request. Status: " + response.getStatusCode());
            }

            log.info("Data sent successfully: {}", response.getBody());

        } catch (Exception e) {
            log.error("Error during purchase request processing, rolling back reservation changes", e);

            // 실패 시 rollback 처리
            for (Seat seat : updatedSeats) {
                seat.setReservationStatus("NO");
                seatRepository.save(seat);
                log.info("Rolled back reservation status for seat: {} to 'NO'", seat);
            }

            throw e; // 예외를 다시 던져 다른 처리기로 전파
        }
    }

    public void saveSeats(List<SeatAndPriceDTO> seatAndPriceDTOs) {
        for (SeatAndPriceDTO dto : seatAndPriceDTOs) {
            for (String eventTime : dto.getEventTime()) {
                for (int i = 1; i <= dto.getCount(); i++) {
                    Seat seat = new Seat(dto.getEventName(), dto.getSection(), i, dto.getPrice(), eventTime);
                    seatRepository.save(seat);
                }
            }
        }
    }

    public String cancelSeat(MinimalSeatDTO seatDTO) {
        // 들어오는 요청 데이터 로깅
        logger.debug("취소 요청 - 이벤트: {}, 섹션: {}, 좌석 번호: {}",
                seatDTO.getEventName(), seatDTO.getSection(), seatDTO.getSeatNumber());

        // 좌석 검색 및 검색 결과 로깅
        Seat seat = seatRepository.findByEventNameAndSectionAndSeatNumberAndEventTimeAndPrice(
                seatDTO.getEventName(),
                seatDTO.getSection(),
                seatDTO.getSeatNumber(),
                seatDTO.getEventTime(),
                seatDTO.getPrice()
        ).orElseThrow(() -> {
            logger.error("주어진 세부 사항으로 좌석을 찾을 수 없음 - 이벤트: {}, 섹션: {}, 좌석 번호: {}",
                    seatDTO.getEventName(), seatDTO.getSection(), seatDTO.getSeatNumber());
            return new IllegalArgumentException("좌석을 찾을 수 없습니다.");
        });

        // 좌석의 현재 예약 상태 로깅
        logger.debug("현재 예약 상태: {}", seat.getReservationStatus());

        // 예약 상태가 "NO"일 경우, 이미 예약이 되어 있지 않음을 나타냄
        if ("NO".equals(seat.getReservationStatus())) {
            logger.warn("좌석 id: {}는 이미 예약되지 않은 상태입니다.", seat.getId());
            throw new IllegalArgumentException("해당 좌석은 이미 예약이 되어 있지 않습니다.");
        }

        // 예약 상태 업데이트 및 변경 사항 로깅
        seat.setReservationStatus("NO");
        logger.debug("좌석 id: {}의 예약 상태를 NO로 업데이트", seat.getId());

        // 변경 사항을 저장소에 저장
        seatRepository.save(seat);
        logger.debug("데이터베이스에 좌석 id: {}의 예약 상태 업데이트 완료", seat.getId());

        return "좌석 예약 상태가 NO로 변경되었습니다.";
    }

    public List<Seat> getAllSeats() {
        // 전체 좌석 조회
        return seatRepository.findAll(); // JpaRepository가 제공하는 기본 메소드 사용
    }

    public List<Seat> getSeatsByEventName(String eventName) {
        // 공연 이름으로 좌석 조회
        return seatRepository.findByEventName(eventName);
    }

}