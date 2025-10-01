package com.example.bemerch.kakao;


import com.example.bemerch.kakao.dto.*;
import com.example.bemerch.merch.dto.purchase.MerchPurchaseDetail;
import com.example.bemerch.merch.dto.refund.MerchRefundDetail;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public KakaoReadyResponse kakaoPayReady(List<MerchPurchaseDetail> merchPurchaseDetails, String email) {
        // total_amount 및 item_name 설정
        int totalAmount = merchPurchaseDetails.stream()
                .mapToInt(MerchPurchaseDetail::getPrice)
                .sum();

        // 첫 번째 항목의 이름과 추가 항목 계산
        String itemNames;
        if (!merchPurchaseDetails.isEmpty()) {
            String firstItemName = merchPurchaseDetails.get(0).getName();
            int additionalItemCount = merchPurchaseDetails.size() - 1;
            itemNames = firstItemName + " 외 " + additionalItemCount + "개";
        } else {
            itemNames = "상품 미등록";
        }

        // 거래 요청 파라미터 설정
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("cid", kakaoPayProperties.getCid());
        parameters.put("partner_order_id", "clove");
        parameters.put("partner_user_id", email);
        parameters.put("item_name", itemNames);
        parameters.put("quantity", merchPurchaseDetails.size());
        parameters.put("total_amount", totalAmount);
        parameters.put("tax_free_amount", 0);
        parameters.put("vat_amount", 0);

        // 결제 관련 URL 설정
        String approval_url= String.format("http://cse.ticketclove.com/%s/merch/kakao/success", NAMESPACE);
        parameters.put("approval_url", approval_url);
        String fail_url= String.format("http://cse.ticketclove.com/%s/merch/kakao/fail", NAMESPACE);
        parameters.put("fail_url", fail_url);
        String cancel_url= String.format("http://cse.ticketclove.com/%s/merch/kakao/cancel", NAMESPACE);
        parameters.put("cancel_url", cancel_url);
        log.info("Parameters for ready request: {}", parameters);

        try {
            log.info("Sending request to KakaoPay API to prepare payment...");
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(parameters, getHeaders());
            String apiUrl = "https://open-api.kakaopay.com/online/v1/payment/ready";

            // API 요청 및 응답 수신
            KakaoReadyResponse kakaoReadyResponse = restTemplate.postForObject(apiUrl, requestEntity, KakaoReadyResponse.class);
            log.info("Payment ready response received: {}", kakaoReadyResponse);

            // DTO 초기화 및 반환
            purchaseResponseDTO = new PurchaseResponseDTO(kakaoReadyResponse, email);
            return kakaoReadyResponse;

        } catch (Exception e) {
            log.error("Error during kakaoPayReady request", e);
            throw new RuntimeException("결제 준비 중 오류가 발생했습니다.", e);
        }
    }

    public KakaoApproveResponse approveResponse(String pgToken) {
        log.info("Starting payment approval request for pgToken: {}", pgToken);

        if (purchaseResponseDTO == null) {
            throw new IllegalStateException("PurchaseResponseDTO is null");
        }
        if (purchaseResponseDTO.getKakaoReadyResponse() == null) {
            throw new IllegalStateException("No valid KakaoReadyResponse found in PurchaseResponseDTO.");
        }

        String tid = purchaseResponseDTO.getKakaoReadyResponse().getTid();
        if (tid == null || tid.isEmpty()) {
            throw new IllegalStateException("No valid TID available in KakaoReadyResponse.");
        }

        String partnerUserId = purchaseResponseDTO.getEmail();
        String partnerOrderId = "clove";

        Map<String, String> parameters = new HashMap<>();
        parameters.put("cid", kakaoPayProperties.getCid());
        parameters.put("tid", tid);
        parameters.put("partner_order_id", partnerOrderId);
        parameters.put("partner_user_id", partnerUserId);
        parameters.put("pg_token", pgToken);

        log.info("KakaoPay request param - CID: {}, TID: {}, Partner Order ID: {}, Partner User ID: {}",
                parameters.get("cid"), parameters.get("tid"), partnerOrderId, partnerUserId);

        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(parameters, getHeaders());

        try {
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

    public KakaoCancelResponse kakaoCancel(List<MerchRefundDetail> merchRefundDetails) {

        int totalAmount = merchRefundDetails.stream()
                .mapToInt(MerchRefundDetail::getPrice)
                .sum();

        String itemNames;
        if (!merchRefundDetails.isEmpty()) {
            String firstItemName = merchRefundDetails.get(0).getName();
            int additionalItemCount = merchRefundDetails.size() - 1;
            itemNames = firstItemName + " 외 " + additionalItemCount + "개";
        } else {
            itemNames = "상품 미등록";
        }

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("cid", kakaoPayProperties.getCid());
        parameters.put("item_name", itemNames);
        parameters.put("tid", merchRefundDetails.get(0).getTid());
        parameters.put("cancel_amount", totalAmount);
        parameters.put("cancel_tax_free_amount", 0);
        parameters.put("cancel_vat_amount", 0);


        log.debug("Parameters for cancel request: {}", parameters);
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(parameters, getHeaders());

        try {
            log.info("Sending cancel request to Kakao Pay API");
            KakaoCancelResponse cancelResponse = restTemplate.postForObject(
                    "https://open-api.kakaopay.com/online/v1/payment/cancel",
                    requestEntity,
                    KakaoCancelResponse.class);
            log.info("Kakao Pay cancel response received successfully");
            log.debug("Cancel response: {}", cancelResponse);
            return cancelResponse;
        } catch (Exception e) {
            log.error("Error occurred during Kakao Pay cancel request", e);
            throw new RuntimeException("결제 취소 중 오류가 발생했습니다.", e);
        }
    }

}

