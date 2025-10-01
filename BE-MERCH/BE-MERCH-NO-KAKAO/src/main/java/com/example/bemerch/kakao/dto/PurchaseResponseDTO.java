package com.example.bemerch.kakao.dto;

import com.example.bemerch.merch.dto.purchase.MerchPurchaseDetail;
import com.example.bemerch.merch.dto.MinimalMerchDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseResponseDTO {
    private KakaoReadyResponse kakaoReadyResponse; // 카카오페이 관련 응답
    private String email;
    private String jwtToken;
    private List<MinimalMerchDTO> merchDTOS;
    private String status;  // "Success" 또는 "Failure"
    private String message; // 실패 이유
    private List<MerchPurchaseDetail> originalMerches; // 원래 클라이언트가 보낸 데이터를 저장

    // 카카오페이 결제 시 예약 좌석 없음
    public PurchaseResponseDTO(KakaoReadyResponse kakaoReadyResponse, String email) {
        this.kakaoReadyResponse = kakaoReadyResponse;
        this.email = email;
    }

    // 카카오페이 결제 시 예약 좌석 없음
    public PurchaseResponseDTO(String status, String message) {
        this.status = status;
        this.message = message;
        this.kakaoReadyResponse = null;
        this.email = null;
        this.jwtToken = null;
        this.merchDTOS = null;
    }

    public PurchaseResponseDTO(KakaoReadyResponse kakaoReadyResponse, String email, String jwtToken) {
        this.kakaoReadyResponse = kakaoReadyResponse;
        this.email = email;
        this.jwtToken = jwtToken;
    }

}
