package com.example.msaeventinformation.service;

import com.example.msaeventinformation.dto.MerchDTO;
import com.example.msaeventinformation.dto.SeatAndPriceDTO;
import com.example.msaeventinformation.model.Merch;
import com.example.msaeventinformation.model.SeatAndPrice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalService {

    private final RestTemplate restTemplate;

    @Value("${NAMESPACE}")
    private String NAMESPACE;

    public void sendSeatAndPriceInfo(SeatAndPrice seatAndPrice,String authToken) {
        String url = "http://localhost:8082/seat";
//        String url = String.format("http://cse.ticketclove.com/%s/seat", NAMESPACE);

        // DTO 생성
        List<SeatAndPriceDTO> dtoList = List.of(new SeatAndPriceDTO(
                seatAndPrice.getSection(),
                seatAndPrice.getPrice(),
                seatAndPrice.getCount(),
                seatAndPrice.getEvent().getName(),
                seatAndPrice.getEvent().getEventTime()
        ));

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + authToken); // 인증 토큰 설정

        // HTTP 엔티티 생성
        HttpEntity<List<SeatAndPriceDTO>> entity = new HttpEntity<>(dtoList, headers);

        // 데이터와 헤더 정보 로그로 출력
        log.info("Sending data to URL: {}", url);
        log.info("Request DTO List: {}", dtoList);
        log.info("HTTP Headers: {}", headers);

        // 데이터를 전송
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Data sent successfully: {}", response.getBody());
            } else {
                log.error("Failed to send data, status code: {}", response.getStatusCode());
            }
        } catch (HttpClientErrorException e) {
            log.error("An error occurred: {}", e.getMessage());
        }
    }

    public void sendMerchInfo(Merch merch, String eventName, String authToken) {
      String url = "http://localhost:8084/merch";  // 예제 URL
//        String url = String.format("http://cse.ticketclove.com/%s/merch", NAMESPACE);


        List<MerchDTO> dtosList = List.of(new MerchDTO(
                merch.getName(),
                merch.getPrice(),
                merch.getCount(),
                merch.getImage(),
                merch.getEvent().getName()
        ));

        // HTTP 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + authToken); // 인증 토큰 설정

        // HTTP 엔티티 생성
        HttpEntity<List<MerchDTO>> entity = new HttpEntity<>(dtosList, headers);

        // 데이터 전송 로그 출력
        log.info("Sending Merch data to URL: {}", url);
        log.info("Merch DTO: {}", dtosList);
        log.info("HTTP Headers: {}", headers);

        // 데이터를 전송
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Merch data sent successfully: {}", response.getBody());
            } else {
                log.error("Failed to send merch data, status code: {}, response: {}", response.getStatusCode(), response.getBody());
            }
        } catch (HttpClientErrorException e) {
            log.error("An error occurred while sending merch data: {}, response body: {}", e.getMessage(), e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            log.error("Service not reachable: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error occurred: {}", e.getMessage(), e);
        }
    }
}
