package com.example.bemsaseat.kakao.dto;

import com.example.bemsaseat.seat.dto.MinimalSeatDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseResponseDTO {
    private KakaoReadyResponse kakaoReadyResponse; // 카카오페이 관련 응답
    private String email;       // 사용자 이메일
    private int seatNumber;     // 구매한 좌석 수
    private String jwtToken;    // JWT 토큰
    private List<MinimalSeatDTO> bookedSeats; // 예약된 좌석 목록

    // 카카오페이 결제 시 예약 좌석 없음
    public PurchaseResponseDTO(KakaoReadyResponse kakaoReadyResponse, String email, int seatNumber) {
        this.kakaoReadyResponse = kakaoReadyResponse;
        this.email = email;
        this.seatNumber = seatNumber;
        this.bookedSeats = null;
    }

    // tid 저장하기 위해서 사용하는 dto 초기화
    public PurchaseResponseDTO(KakaoReadyResponse kakaoReadyResponse, String email, int seatNumber, String jwtToken) {
        this.kakaoReadyResponse = kakaoReadyResponse;
        this.email = email;
        this.seatNumber = seatNumber;
        this.jwtToken = jwtToken;
        this.bookedSeats = null;
    }

    // 예약 정보 생성자
    public PurchaseResponseDTO(List<MinimalSeatDTO> bookedSeats, String email, String jwtToken) {
        this.bookedSeats = bookedSeats;
        this.email = email;
        this.jwtToken = jwtToken;
        this.seatNumber = bookedSeats.size(); // 예약된 좌석 수
    }

}
