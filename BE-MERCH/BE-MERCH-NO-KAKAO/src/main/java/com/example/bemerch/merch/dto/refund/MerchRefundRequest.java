package com.example.bemerch.merch.dto.refund;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchRefundRequest {
    private List<MerchRefundDetail> merchRefundDetails; // 여러 좌석 정보를 담는 리스트
}
