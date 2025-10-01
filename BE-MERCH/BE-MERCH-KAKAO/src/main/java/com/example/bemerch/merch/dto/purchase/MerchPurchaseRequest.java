package com.example.bemerch.merch.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchPurchaseRequest {
    private List<MerchPurchaseDetail> Merches; // 여러 좌석 정보를 담는 리스트
}

