package com.example.bemsaseat.seat.service;


import com.example.bemsaseat.kakao.KakaoPayService;
import com.example.bemsaseat.kakao.dto.KakaoReadyResponse;
import com.example.bemsaseat.kakao.dto.PurchaseResponseDTO;
import com.example.bemsaseat.seat.dto.MinimalSeatDTO;
import com.example.bemsaseat.seat.dto.SeatAndPriceDTO;
import com.example.bemsaseat.seat.dto.SeatDetail;
import com.example.bemsaseat.seat.dto.SeatPurchaseRequest;
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
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.example.bemsaseat.seat.config.RandomStringGenerator.generateRandomString;

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

        // 전체 좌석을 검증 수행
        for (SeatDetail seatDetail : seatRequest.getSeats()) {
            Optional<Seat> optionalSeat = seatRepository.findByEventNameAndSectionAndSeatNumberAndPriceAndEventTimeAndReservationStatus(
                    seatDetail.getEventName(),
                    seatDetail.getSection(),
                    seatDetail.getSeatNumber(),
                    seatDetail.getPrice(),
                    seatDetail.getEventTime(),
                    "NO"
            );

            if (!optionalSeat.isPresent()) {
                log.error("Seat not available: EventName={}, Section={}, SeatNumber={}, EventTime={}",
                        seatDetail.getEventName(), seatDetail.getSection(), seatDetail.getSeatNumber(), seatDetail.getEventTime());
                throw new IllegalArgumentException("이미 구매된 자리입니다.");
            }

            Seat seat = optionalSeat.get();
            if (seat.getPrice() != seatDetail.getPrice()) {
                log.error("Price mismatch for seat: eventName={}, section={}, seatNumber={}, expectedPrice={}, actualPrice={}",
                        seatDetail.getEventName(), seatDetail.getSection(), seatDetail.getSeatNumber(),
                        seatDetail.getPrice(), seat.getPrice());
                throw new IllegalArgumentException("가격이 일치하지 않습니다.");
            }

            // MinimalSeatDTO 생성 및 추가, 일단 여기서 0으로 좌석 번호 초기화
            MinimalSeatDTO minimalSeatDTO = new MinimalSeatDTO(
                    seatDetail.getEventName(),
                    seatDetail.getSection(),
                    seatDetail.getSeatNumber(),
                    seatDetail.getPrice(),
                    seatDetail.getEventTime(),
                    LocalDate.now(),
                    LocalTime.now(),
                    "",  // 이후에 TID를 설정할 것이므로 초기화 상태로 남겨둔 부분
                    NAMESPACE
            );

            minimalSeatDTOList.add(minimalSeatDTO);
        }

        // pay 값에 따라 처리
        if (pay) {
            // 카카오페이 결제 준비 로직
            log.info("All seats validated, preparing KakaoPay payment...");
            KakaoReadyResponse readyResponse = kakaoPayService.kakaoPayReady(seatRequest.getSeats(), email);
            String tid = readyResponse.getTid();
            log.info("KakaoPay payment ready response received, creating PurchaseResponseDTO...");

            for (MinimalSeatDTO seatDTO : minimalSeatDTOList) {
                seatDTO.setTid(tid);
            }
            return new PurchaseResponseDTO(readyResponse, email, minimalSeatDTOList.size(), jwtToken,minimalSeatDTOList); // 카카오페이 결제 응답
        } else {

            for (SeatDetail seatDetail : seatRequest.getSeats()) {
                Seat seat = seatRepository.findByEventNameAndSectionAndSeatNumberAndEventTime(
                        seatDetail.getEventName(),
                        seatDetail.getSection(),
                        seatDetail.getSeatNumber(),
                        seatDetail.getEventTime()
                ).orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));
            }
            String tid = generateRandomString(16);
            for (MinimalSeatDTO seatDTO : minimalSeatDTOList) {
                seatDTO.setTid(tid);
            }

            // 티켓 서버에 예약 요청 전송
            log.info("Sending reservation requests to ticket server with seat information...");
            sendPurchaseRequest(minimalSeatDTOList, jwtToken);

            // 모든 예약된 좌석에 대해 예약 상태를 YES로 변경
            for (MinimalSeatDTO seatDTO : minimalSeatDTOList) {
                Seat seat = seatRepository.findByEventNameAndSectionAndSeatNumberAndEventTime(
                        seatDTO.getEventName(),
                        seatDTO.getSection(),
                        seatDTO.getSeatNumber(),
                        seatDTO.getEventTime()
                ).orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));

                // 좌석 예약 상태를 YES로 업데이트
                seat.setReservationStatus("YES");
                seatRepository.save(seat);
            }

            // 모든 예약된 좌석 정보를 담은 PurchaseResponseDTO 객체를 반환
            PurchaseResponseDTO purchaseResponse = new PurchaseResponseDTO();
            purchaseResponse.setBookedSeats(minimalSeatDTOList); // 예약된 좌석 정보 설정
            purchaseResponse.setEmail(email);
            purchaseResponse.setJwtToken(jwtToken);
            purchaseResponse.setSeatNumber(minimalSeatDTOList.size()); // 예약된 좌석 수 설정

            log.info("Returning booked seats information: {}", purchaseResponse);
            return purchaseResponse;
        }
    }

    public void sendPurchaseRequest(List<MinimalSeatDTO> minimalSeatDTOs, String jwtToken) {
        log.debug("Starting sendPurchaseRequest with {} seats", minimalSeatDTOs.size());

        minimalSeatDTOs.forEach(seatDTO ->
                log.debug("Seat Info - Event: {}, Section: {}, Seat Number: {}, Price: {}, Event Time: {}",
                        seatDTO.getEventName(), seatDTO.getSection(), seatDTO.getSeatNumber(), seatDTO.getPrice(), seatDTO.getEventTime())
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        // 만약 jwtToken이 "Bearer "로 시작한다면 그 부분을 제거
        if (jwtToken.startsWith("Bearer ")) {
            jwtToken = jwtToken.substring(7);
        }

        headers.setBearerAuth(jwtToken);
        // Log to see what headers are being set
        log.info("Headers being sent: {}", headers);

        HttpEntity<List<MinimalSeatDTO>> request = new HttpEntity<>(minimalSeatDTOs, headers);
        log.info("Request body: {}", request.getBody());

        String url= String.format("http://cse.ticketclove.com/%s/ticket/purchase", NAMESPACE);
//        String url = "http://cse.ticketclove.com/default/ticket/purchase";
//        String url = String.format("http://localhost:8083/ticket/purchase");
        ResponseEntity<String> response = restTemplate.postForEntity(
                url,
                request,
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            for (MinimalSeatDTO seatDTO : minimalSeatDTOs) {
                String updateResult = confirmSeatPurchase(seatDTO);
                log.info(updateResult);
            }
        } else {
            throw new RuntimeException("Failed to complete purchase request. Status: " + response.getStatusCode());
        }
    }

    @Transactional
    public String confirmSeatPurchase(MinimalSeatDTO seatDTO) {
        log.info("Confirming seat purchase for event: {}, section: {}, seatNumber: {}, eventTime: {}",
                seatDTO.getEventName(), seatDTO.getSection(), seatDTO.getSeatNumber(), seatDTO.getEventTime());

        Seat seat = seatRepository.findByEventNameAndSectionAndSeatNumberAndEventTime(
                seatDTO.getEventName(),
                seatDTO.getSection(),
                seatDTO.getSeatNumber(),
                seatDTO.getEventTime()
        ).orElseThrow(() -> new IllegalArgumentException("좌석을 찾을 수 없습니다."));

        seat.setReservationStatus("YES");
        seatRepository.save(seat);

        return String.format("좌석 %d의 예약 상태가 YES로 변경되었습니다.", seatDTO.getSeatNumber());
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

    public List<Seat> getSeats(String eventName, String section) {
        return seatRepository.findByEventNameAndSectionAndReservationStatus(eventName, section, "NO");
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
        );

        // 좌석이 없는 경우 에러 로깅
        if (seat == null) {
            logger.error("주어진 세부 사항으로 좌석을 찾을 수 없음 - 이벤트: {}, 섹션: {}, 좌석 번호: {}",
                    seatDTO.getEventName(), seatDTO.getSection(), seatDTO.getSeatNumber());
            throw new IllegalArgumentException("좌석을 찾을 수 없습니다.");
        }

        // 예약 상태 업데이트 전 로그
        logger.debug("현재 예약 상태: {}", seat.getReservationStatus());

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