package com.example.bemerch.kakao.dto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "kakaopay")
@Data
public class KakaoPayProperties {
    private String secretKey;
    private String cid;
}