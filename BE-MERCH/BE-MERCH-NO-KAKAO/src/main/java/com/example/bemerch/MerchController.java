package com.example.bemerch;



import com.example.bemerch.auth.jwt.TokenProvider;
import com.example.bemerch.kakao.KakaoPayService;
import com.example.bemerch.kakao.dto.KakaoApproveResponse;
import com.example.bemerch.kakao.dto.PurchaseResponseDTO;
import com.example.bemerch.merch.dto.*;
import com.example.bemerch.merch.dto.purchase.MerchPurchaseRequest;
import com.example.bemerch.merch.dto.refund.MerchRefundRequest;
import com.example.bemerch.merch.model.Merch;
import com.example.bemerch.merch.repository.MerchRepository;
import com.example.bemerch.merch.service.MerchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/merch")
@Slf4j
public class MerchController {

    private final MerchService merchService;
    private final TokenProvider tokenProvider;
    private final KakaoPayService kakaoPayService;
    private final MerchRepository merchRepository;
    private List<PurchaseResponseDTO> PurchaseResponseDTOList = new ArrayList<>();

    @Value("${pay}")
    private Boolean pay;

    @Value("${NAMESPACE}")
    private String NAMESPACE;

    @GetMapping("/all")
    public List<Merch> getAllMerch() {
        return merchService.getAllMerch();
    }

    @GetMapping("/byEmail")
    public List<Merch> getMerchByEmail(@RequestHeader("Authorization") String token) {
        token = token.substring(7);
        String email = tokenProvider.getEmailFromToken(token);
        log.info("email: " + email);
        return merchService.getMerchByEmail(email);
    }

    //구매자가 사용
    @PostMapping("/buy")
    public ResponseEntity<PurchaseResponseDTO> purchaseSeats(
            @RequestBody MerchPurchaseRequest merchPurchaseRequest,
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
            PurchaseResponseDTO response = merchService.purchaseMerches(merchPurchaseRequest, email, jwtToken);
            response.setOriginalMerches(merchPurchaseRequest.getMerches()); // 원본 데이터를 설정
            // 이걸해야 dto 사용가능 중요!!
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

    @GetMapping("/kakao/success")
    public ResponseEntity<List<MinimalMerchDTO>> afterPayRequest(@RequestParam("pg_token") String pgToken) {
        try {
            // 카카오 결제 승인 요청 처리
            KakaoApproveResponse kakaoApproveResponse = kakaoPayService.approveResponse(pgToken);
            log.info("Payment approved: {}", kakaoApproveResponse);

            // KakaoReadyResponse의 TID로 PurchaseResponseDTO를 찾아 원본 데이터 활용
            PurchaseResponseDTO purchaseResponse = PurchaseResponseDTOList.stream()
                    .filter(dto -> dto.getKakaoReadyResponse().getTid().equals(kakaoApproveResponse.getTid()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("PurchaseResponseDTO not found"));
            // Merch와 재고 업데이트
            List<MinimalMerchDTO> minimalMerchDTOList = merchService.updateMerchesAndStock(purchaseResponse, kakaoApproveResponse);

            // 리다이렉션할 URL 설정
            String redirectUrl = String.format("http://cse.ticketclove.com/%s/page/play", NAMESPACE);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            log.info("Redirecting to: {}", redirectUrl);

            // 최종 응답 반환
            return new ResponseEntity<>(minimalMerchDTOList, headers, HttpStatus.FOUND);

        } catch (Exception e) {
            log.error("Error during payment success processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
/*
            // 원래의 상품 정보 사용하여 Merch 업데이트
            for (MerchPurchaseDetail originalMerch : purchaseResponse.getOriginalMerches()) {

                String name = originalMerch.getName();
                int price = originalMerch.getPrice();
                String eventName = originalMerch.getEventName();
                Optional<MerchIndex> merchIndexOpt = merchIndexRepository.findByName(name);
                MerchIndex merchIndex = merchIndexOpt.get();
                int soldCount = merchIndex.getSoldCount()+1;

                Optional<Merch> existingMerchOpt = merchRepository.findByNameAndPriceAndEventNameAndIdAndSoldIsFalse(name, price, eventName,soldCount);

                if (existingMerchOpt.isPresent()) {
                    // Merch의 판매 상태 업데이트
                    Merch merch = new Merch();
                    merch.setName(originalMerch.getName());
                    merch.setPrice(originalMerch.getPrice());
                    merch.setEventName(originalMerch.getEventName());
                    merch.setEmail(kakaoApproveResponse.getPartner_user_id()); // 사용자 이메일 업데이트
                    merch.setTid(kakaoApproveResponse.getTid()); // TID 업데이트
                    merch.setSold(true); // 판매 상태로 설정

                    // 데이터베이스에 Merch 저장 (업데이트)
                    merchRepository.save(merch);
                    log.info("Merch updated: {}", merch);

                    //soldCount 변경
                    merchIndex.setSoldCount(soldCount); // soldCount 증가
                    merchIndexRepository.save(merchIndex); // MerchIndex 저장
                    log.info("Updated MerchIndex: {}", merchIndex);

                    // MinimalMerchDTO 생성
                    MinimalMerchDTO minimalMerchDTO = new MinimalMerchDTO(
                            merch.getName(),
                            merch.getPrice(),
                            merch.getEventName(),
                            merch.getEmail(),
                            merch.getTid()
                    );
                    minimalMerchDTOList.add(minimalMerchDTO);
                    log.info("MinimalMerchDTO created: {}", minimalMerchDTO);
                } else {
                    log.warn("Merch not found or already sold for name: {}, price: {}, event: {}",
                            originalMerch.getName(), originalMerch.getPrice(), originalMerch.getEventName());
                }
            }

            // 리다이렉션할 URL 설정
            String redirectUrl = String.format("http://cse.ticketclove.com/%s/page/play/result", NAMESPACE);
            HttpHeaders headers = new HttpHeaders();
            headers.setLocation(URI.create(redirectUrl));
            log.info("Redirecting to: {}", redirectUrl);

            return new ResponseEntity<>(minimalMerchDTOList, headers, HttpStatus.FOUND);

        } catch (Exception e) {
            log.error("Error during payment success processing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        */

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

    @DeleteMapping("/refund")
    public ResponseEntity<String> cancelTicket(@RequestBody MerchRefundRequest refundRequest,
                                               @RequestHeader("Authorization") String authorizationHeader) {

        log.info("Received request to cancel ticket: {}", refundRequest);
        try {
            String resultMessage = merchService.cancelTicket(refundRequest, authorizationHeader);
            log.info("Ticket cancellation successful");
            return ResponseEntity.ok(resultMessage);
        } catch (IllegalArgumentException e) {
            log.error("Invalid input during ticket cancellation", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            log.error("Error occurred during ticket cancellation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("티켓 취소 처리 중 오류 발생");
        }
    }

    //판매자가 요청하면 자동 생성
    @PostMapping
    public ResponseEntity<String> createMerches(@RequestBody List<ReceiveMerch> receiveMerches) {
        log.info("receive merches: {}", receiveMerches);
        try {
            merchService.saveMerches(receiveMerches);
            return ResponseEntity.ok("Seats created successfully");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to create seats");
        }
    }

}