package com.example.bemsaticket.kakao;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class KakaoPayService {

    private final KakaoPayProperties kakaoPayProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    private HttpHeaders getHeaders() {
        log.debug("Building headers with secret key");
        HttpHeaders headers = new HttpHeaders();
        String auth = "SECRET_KEY " + kakaoPayProperties.getSecretKey();
        headers.set("Authorization", auth);
        headers.set("Content-Type", "application/json");
        log.debug("Headers built successfully: {}", headers);
        return headers;
    }

    public KakaoCancelResponse kakaoCancel(String tid, int cancelAmount, int cancelTaxFreeAmount, int cancelVatAmount) {
        log.info("Starting Kakao Pay cancel request for TID: {}", tid);
        log.debug("Cancel amount: {}, Tax-free amount: {}, VAT amount: {}", cancelAmount, cancelTaxFreeAmount, cancelVatAmount);

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("cid", kakaoPayProperties.getCid());
        parameters.put("tid", tid);
        parameters.put("cancel_amount", cancelAmount);
        parameters.put("cancel_tax_free_amount", cancelTaxFreeAmount);
        parameters.put("cancel_vat_amount", cancelVatAmount);


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