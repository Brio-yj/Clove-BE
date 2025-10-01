package com.example.bemsaseat;


import com.example.bemsaseat.auth.jwt.TokenProvider;
import com.example.bemsaseat.kakao.KakaoPayService;
import com.example.bemsaseat.kakao.dto.KakaoApproveResponse;
import com.example.bemsaseat.kakao.dto.KakaoReadyResponse;
import com.example.bemsaseat.kakao.dto.PurchaseResponseDTO;
import com.example.bemsaseat.seat.dto.MinimalSeatDTO;
import com.example.bemsaseat.seat.dto.SeatAndPriceDTO;
import com.example.bemsaseat.seat.dto.SeatPurchaseRequest;
import com.example.bemsaseat.seat.model.Seat;
import com.example.bemsaseat.seat.service.SeatService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.token.TokenService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/seat")
@Slf4j
public class SeatController {

    private final SeatService seatService;
    private final TokenProvider tokenProvider;
    private final KakaoPayService kakaoPayService;
    private List<PurchaseResponseDTO> PurchaseResponseDTOList = new ArrayList<>();

    @Value("${pay}")
    private Boolean pay;

    @Value("${NAMESPACE}")
    private String NAMESPACE;

    //구매자가 사용
    @PostMapping("/buy")
    public ResponseEntity<PurchaseResponseDTO> purchaseSeats(@RequestBody SeatPurchaseRequest seatPurchaseRequest,
                                                             HttpServletRequest request) {
        try {
            String bearerToken = request.getHeader("Authorization");
            if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith("Bearer ")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 토큰이 유효하지 않을 때
            }
            String token = bearerToken.substring(7);

            if (!tokenProvider.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build(); // 토큰이 유효하지 않음
            }

            String email = tokenProvider.getEmailFromToken(token);
            String jwtToken = token; // 이미 검증됨

            // 좌석 구매 처리
            PurchaseResponseDTO response = seatService.purchaseSeats(seatPurchaseRequest, email, jwtToken);
            //이걸해야 dto 사용가능 중요!!
            PurchaseResponseDTOList.add(response);
            log.info("PurchaseResponseDTO response: {}", response);

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        } catch (Exception e) {
            log.error("구매 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    //카카오 페이 리다이렉션
    @GetMapping("/kakao/success")
    public ResponseEntity<?> afterPayRequest(@RequestParam("pg_token") String pgToken) {
        try {
            // 결제 승인 요청 및 응답
            KakaoApproveResponse kakaoApproveResponse = kakaoPayService.approveResponse(pgToken);
            log.info("Payment approved: {}", kakaoApproveResponse);

            // TID를 이용해 PurchaseResponseDTO 찾기
            String tid = kakaoApproveResponse.getTid();
            PurchaseResponseDTO purchaseResponse = PurchaseResponseDTOList.stream()
                    .filter(dto -> dto.getKakaoReadyResponse().getTid().equals(tid))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("PurchaseResponseDTO not found"));
            log.info("PurchaseResponseDTO response: {}", purchaseResponse);

            // 임시 저장된 최소 좌석 DTO 정보를 가져옴
            List<MinimalSeatDTO> minimalSeatDTOList = purchaseResponse.getBookedSeats();
            String jwtToken = purchaseResponse.getJwtToken();

            log.info("minimalSeatDTOList: {}", minimalSeatDTOList);
            log.info("jwtToken: {}", jwtToken);
            // 외부 서비스로 좌석 구매 요청 보내기
            seatService.sendPurchaseRequest(minimalSeatDTOList, jwtToken);

            log.info("Successfully processed and sent seat purchase request.");

            // 리다이렉션할 URL 설정
            String redirectUrl = String.format("http://cse.ticketclove.com/%s/page/play", NAMESPACE);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            return new ResponseEntity<>(headers, HttpStatus.FOUND);

        } catch (Exception e) {
            log.error("Error during payment success processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    //카카오 페이 리다이렉션
    @GetMapping("/kakao/cancel")
    public ResponseEntity<Void> cancel() {
        log.info("Payment cancellation request received");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    //카카오 페이 리다이렉션
    @GetMapping("/kakao/fail")
    public ResponseEntity<Void> fail() {
        log.info("Payment failure request received");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }


    //판매자가 요청하면 자동 생성
    @PostMapping
    public ResponseEntity<String> createSeats(@RequestBody List<SeatAndPriceDTO> seatAndPriceDTOs) {
        try {
            seatService.saveSeats(seatAndPriceDTOs);
            return ResponseEntity.ok("Seats created successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to create seats");
        }
    }


    //구매자가 요청하면 자동실행
    @PutMapping("/cancel")
    public ResponseEntity<String> cancelSeat(@RequestBody MinimalSeatDTO seatDTO) {
        // 처음 요청이 들어왔을 때 로깅
        log.info("좌석 취소 요청 수신 - 이벤트: {}, 섹션: {}, 좌석 번호: {}",
                seatDTO.getEventName(), seatDTO.getSection(), seatDTO.getSeatNumber());

        try {
            // Service layer에서 결과 메시지 받아오기
            String resultMessage = seatService.cancelSeat(seatDTO);
            log.info("좌석 취소 처리 결과: {}", resultMessage);

            // 성공 시 응답
            return ResponseEntity.ok(resultMessage);
        } catch (IllegalArgumentException e) {
            // 입력 값이 올바르지 않을 때 - 예외 메시지 로깅
            log.warn("입력 데이터 유효성 실패 - 예외 메시지: {}", e.getMessage());

            // 잘못된 요청에 대한 응답
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            // 기타 예외 발생 시 - 상세 예외 로깅
            log.error("서버 처리 중 오류 발생 - 예외 메시지: {}", e.getMessage(), e);

            // 서버 오류에 대한 응답
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("예약 상태 변경 처리 중 오류 발생");
        }
    }

    //구매자,판매자가 전체 보기 할때 사용
    @GetMapping
    public ResponseEntity<List<Seat>> getAllSeats() {
        try {
            List<Seat> seats = seatService.getAllSeats();
            return ResponseEntity.ok(seats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    //구매자,판매자가 특정 공연 좌석 볼때 사용
    @GetMapping("/{eventName}")
    public ResponseEntity<List<Seat>> getSeatsByEventName(@PathVariable String eventName) {
        try {
            List<Seat> seats = seatService.getSeatsByEventName(eventName);
            return ResponseEntity.ok(seats);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}