package com.example.bemsaseat.kakao;

import com.example.bemsaseat.kakao.dto.KakaoApproveResponse;
import com.example.bemsaseat.kakao.dto.KakaoPayProperties;
import com.example.bemsaseat.kakao.dto.KakaoReadyResponse;
import com.example.bemsaseat.kakao.dto.PurchaseResponseDTO;
import com.example.bemsaseat.seat.dto.SeatDetail;
import com.example.bemsaseat.seat.dto.SeatPurchaseDetail;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class KakaoPayService {

    private final KakaoPayProperties kakaoPayProperties;
    private final RestTemplate restTemplate = new RestTemplate();
    private PurchaseResponseDTO purchaseResponseDTO = new PurchaseResponseDTO();
    private KakaoReadyResponse kakaoReadyResponse;

    @Value("${NAMESPACE}")
    private String NAMESPACE;

    @Value("${pay}")
    private Boolean pay;



    private HttpHeaders getHeaders() {
        log.debug("Building headers with secret key");
        HttpHeaders headers = new HttpHeaders();
        String auth = "SECRET_KEY " + kakaoPayProperties.getSecretKey();
        headers.set("Authorization", auth);
        headers.set("Content-Type", "application/json");
        log.debug("Headers built successfully: {}", headers);
        return headers;
    }

    public KakaoReadyResponse kakaoPayReady(List<SeatPurchaseDetail> seatDetails, String email) {

        //여기서도 결국 좌석 번호는 필요없다

        Map<String, Object> parameters = new HashMap<>();
        int totalAmount = 0;
        StringBuilder itemNames = new StringBuilder();

        if (!seatDetails.isEmpty()) {
            // 첫 번째 아이템 처리
            SeatPurchaseDetail firstSeat = seatDetails.get(0);
            itemNames.append(firstSeat.getEventName())
                    .append("-")
                    .append(firstSeat.getSection())
                    .append("-")
                    .append(firstSeat.getEventTime());

            // 나머지 아이템에 대한 수량
            int additionalItemsCount = seatDetails.size() - 1;
            if (additionalItemsCount > 0) {
                itemNames.append(" 외 ").append(additionalItemsCount).append("개");
            }
        }

        // 금액 합산과 로그 출력
        for (SeatPurchaseDetail seatDetail : seatDetails) {
            totalAmount += seatDetail.getPrice();
            log.debug("Adding seat to order: eventName={}, section={},  price={}, eventTime={}",
                    seatDetail.getEventName(), seatDetail.getSection(), seatDetail.getPrice(), seatDetail.getEventTime());
        }

        // 거래 요청 파라미터 설정
        parameters.put("cid", kakaoPayProperties.getCid());
        parameters.put("partner_order_id", "clove");
        parameters.put("partner_user_id", email);
        parameters.put("item_name", itemNames.toString());
        parameters.put("quantity", String.valueOf(seatDetails.size()));
        parameters.put("total_amount", totalAmount);
        parameters.put("tax_free_amount", 0);
        parameters.put("vat_amount", 0);
//        String approval_url = "http://localhost:8082/seat/kakao/success";
        String approval_url = String.format("http://cse.ticketclove.com/%s/seat/kakao/success", NAMESPACE);
        parameters.put("approval_url", approval_url);
        String fail_url = String.format("http://cse.ticketclove.com/%s/seat/kakao/fail", NAMESPACE);
        parameters.put("fail_url", fail_url);
        String cancel_url = String.format("http://cse.ticketclove.com/%s/seat/kakao/cancel", NAMESPACE);
        parameters.put("cancel_url", cancel_url);

        log.debug("Parameters for ready request: {}", parameters);

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(parameters, getHeaders());
        String apiUrl = "https://open-api.kakaopay.com/online/v1/payment/ready";

        RestTemplate restTemplate = new RestTemplate();

        try {
            log.info("Sending request to KakaoPay API to prepare payment...");
            KakaoReadyResponse kakaoReadyResponse = restTemplate.postForObject(apiUrl, requestEntity, KakaoReadyResponse.class);
            log.info("Payment ready response received: {}", kakaoReadyResponse);

            // 이게 있어야 DTO 저장된다
            purchaseResponseDTO = new PurchaseResponseDTO(kakaoReadyResponse, email,seatDetails.size(),null );

            return kakaoReadyResponse;

        } catch (Exception e) {
            log.error("Error during kakaoPayReady request", e);
            throw new RuntimeException("결제 준비 중 오류가 발생했습니다.", e);
        }
    }

    public KakaoApproveResponse approveResponse(String pgToken) {
        log.info("Starting payment approval request for pgToken: {}", pgToken);

        Map<String, String> parameters = new HashMap<>();
        String cid = kakaoPayProperties.getCid();
        log.debug("Payment approval - CID: {}", cid);

        // Validate that purchaseResponseDTO is not null and has a valid KakaoReadyResponse
        if (purchaseResponseDTO == null) {
            log.error("PurchaseResponseDTO is null. Cannot proceed without a valid purchase response.");
            throw new IllegalStateException("PurchaseResponseDTO is null. Cannot proceed with payment approval.");
        }

        if (purchaseResponseDTO.getKakaoReadyResponse() == null) {
            log.error("KakaoReadyResponse is null in PurchaseResponseDTO. Cannot proceed without a valid TID.");
            throw new IllegalStateException("No valid KakaoReadyResponse found in PurchaseResponseDTO.");
        }

        String tid = purchaseResponseDTO.getKakaoReadyResponse().getTid();
        if (tid == null || tid.isEmpty()) {
            log.error("TID is not available in KakaoReadyResponse. Cannot proceed with payment approval.");
            throw new IllegalStateException("No valid TID available in KakaoReadyResponse.");
        }


        String partnerUserId = purchaseResponseDTO.getEmail();
        String partnerOrderId = "clove";
        parameters.put("cid", cid);
        parameters.put("tid", tid);
        parameters.put("partner_order_id", partnerOrderId);
        parameters.put("partner_user_id", partnerUserId);
        parameters.put("pg_token", pgToken);


        log.info("KakaoPay request param - CID: {}, TID: {}, Partner Order ID: {}, Partner User ID: {}",
                cid, tid, partnerOrderId, partnerUserId);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(parameters, getHeaders());

        try {
            log.info("Sending request to KakaoPay API for payment approval...");
            KakaoApproveResponse approveResponse = restTemplate.postForObject(
                    "https://open-api.kakaopay.com/online/v1/payment/approve",
                    requestEntity,
                    KakaoApproveResponse.class);
            log.info("Payment approval completed successfully with response: {}", approveResponse);
            return approveResponse;
        } catch (Exception e) {
            log.error("Error during payment approval request", e);
            throw new RuntimeException("결제 승인 중 오류가 발생했습니다.", e);
        }
    }

}

