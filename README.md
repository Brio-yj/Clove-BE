# Clove BE (티켓 커머스 MSA)

> 공연 티켓팅과 굿즈 판매를 지원하는 Clove 백엔드 모놀리포입니다. Auth, Event, Seat, Ticket, Merch 서비스로 구성된 MSA 예제 프로젝트로, JWT 기반 인증과 Redis 분산락, 카카오페이 결제 연동을 핵심으로 합니다.

# 전체 프로젝트 링크
https://github.com/pnucse-capstone-2024/Capstone-2024-team-20

## 전체 아키텍처

<img width="1493" height="998" alt="클로브 아키텍처" src="https://github.com/user-attachments/assets/713b9e71-61db-48be-a8b8-996b9cd5fa07" />

| 모듈 | 책임 | 대표 클래스 |
| --- | --- | --- |
| **BE-AUTH** | 회원 인증, JWT Access/Refresh 토큰 발급 및 재발급,  | [`TokenProvider`](BE-AUTH/src/main/java/com/example/msaauth/jwt/TokenProvider.java) |
| **BE-EVENT** | 공연/세션 등록·수정, S3 이미지 업로드, 판매자 검증 | [`EventService`](BE-EVENT/src/main/java/com/example/msaeventinformation/service/EventService.java) |
| **BE-SEAT** | 좌석 검증·선점, Redis 분산락, 카카오페이 결제 준비/승인, 티켓 서버 연동 | [`SeatService`](BE-SEAT/BE-SEAT-YES-NUMBER-YES-KAKAO/src/main/java/com/example/bemsaseat/seat/service/SeatService.java) |
| **BE-TICKET** | 구매 확정 좌석으로 티켓 발급, 재고 동기화 | [`TicketService`](BE-TICKET/BE-TICKET-NO-KAKAO/src/main/java/com/example/bemsaticket/ticket/service/TicketService.java) |
| **BE-MERCH** | 공연 굿즈 판매, 카카오페이 결제/환불 | [`MerchService`](BE-MERCH/BE-MERCH-KAKAO/src/main/java/com/example/bemerch/merch/service/MerchService.java) |

## 인증 & 권한 (JWT)

Auth 서비스는 `TokenProvider`를 통해 사용자 컨텍스트를 토큰에 주입하여 모든 마이크로서비스에서 일관되게 활용할 수 있게 합니다.

```java
// BE-AUTH/src/main/java/com/example/msaauth/jwt/TokenProvider.java
String accessToken = Jwts.builder()
        .setSubject(authentication.getName())
        .claim("auth", authorities)            // 권한 정보
        .claim("memberId", memberId)          // 회원 식별자
        .claim("memberEmail", member.getEmail())
        .claim("memberAuthority", member.getAuthority().name())
        .setExpiration(accessTokenExpiresIn)
        .signWith(key, SignatureAlgorithm.HS512)
        .compact();
```

- Access 토큰은 100분, Refresh 토큰은 7일 TTL을 갖고 `MemberRepository`를 통해 멤버 정보와 연결됩니다.
- 도메인 서비스들은 공통 `JwtAuthenticationFilter`를 적용하여 요청 헤더에서 토큰을 파싱하고 `SecurityContext`에 사용자 정보를 주입합니다.
- Refresh 토큰으로 재발급 시 `getAuthenticationFromRefreshToken`이 토큰의 Subject를 멤버 ID로 해석해 다시 `UserDetails`를 로드합니다.

## 좌석 충돌 방지 (Redis 분산락)

동시에 같은 좌석을 고르는 상황을 해결하기 위해 Seat 서비스는 Redis 기반 분산락을 사용합니다.

```java
// BE-SEAT/BE-SEAT-YES-NUMBER-NO-KAKAO/src/main/java/com/example/seat/RedisLockRepository.java
public boolean lock(Long seatId) {
    return Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + seatId, "locked", Duration.ofSeconds(3))
    );
}
```

- `RedisLockRepository.lock`이 `SETNX`와 TTL을 이용해 좌석 키(`lock:seat:{id}`)를 생성하고 실패 시 바로 예외를 던져 경쟁 요청을 차단합니다.
- `SeatService.reserveSeat`는 락 획득/해제를 `try/finally`로 감싸 좌석 예약 로직이 실패해도 락이 해제되도록 보장합니다.
- 결제 전 검증 단계에서는 좌석 가격, 상태 등을 다시 확인하여 동시성 오류를 줄입니다.

## 카카오페이 결제 흐름

Seat·Merch 서비스는 카카오페이 `Ready → Approve → (optional) Cancel` 플로우를 구현하여 외부 결제와 내부 좌석/재고 상태를 동기화합니다.

### 1. 결제 준비 (Ready)

```java
// BE-SEAT/BE-SEAT-YES-NUMBER-NO-KAKAO/src/main/java/com/example/bemsaseat/kakao/KakaoPayService.java
public KakaoReadyResponse kakaoPayReady(List<SeatDetail> seatDetails, String email) {
    Map<String, Object> parameters = new HashMap<>();
    parameters.put("cid", kakaoPayProperties.getCid());
    parameters.put("partner_order_id", "clove");
    parameters.put("partner_user_id", email);
    parameters.put("item_name", itemNames(seatDetails));
    parameters.put("quantity", seatDetails.size());
    parameters.put("total_amount", totalAmount(seatDetails));
    parameters.put("approval_url", successUrl());
    // ... fail/cancel URL 설정
    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(parameters, getHeaders());
    KakaoReadyResponse response = restTemplate.postForObject(READY_API, requestEntity, KakaoReadyResponse.class);
    purchaseResponseDTO = new PurchaseResponseDTO(response, email, 0);
    return response;
}
```

- 좌석 정보로 주문명을 구성하고 총액을 계산한 뒤 카카오페이 오픈 API `/payment/ready`에 전송합니다.
- 응답의 `tid`는 `PurchaseResponseDTO`에 저장되어 이후 승인/환불 단계에서 참조됩니다.

Seat 서비스는 `pay=true`인 요청에 대해 준비 응답을 받아 좌석 DTO에 `tid`를 주입한 뒤 클라이언트에 결제 URL을 전달합니다.

### 2. 결제 승인 (Approve)

```java
// BE-SEAT/BE-SEAT-YES-NUMBER-NO-KAKAO/src/main/java/com/example/bemsaseat/kakao/KakaoPayService.java
public KakaoApproveResponse approveResponse(String pgToken) {
    validatePurchaseContext();
    Map<String, String> parameters = new HashMap<>();
    parameters.put("cid", kakaoPayProperties.getCid());
    parameters.put("tid", purchaseResponseDTO.getKakaoReadyResponse().getTid());
    parameters.put("pg_token", pgToken);
    KakaoApproveResponse approveResponse = restTemplate.postForObject(APPROVE_API, requestEntity, KakaoApproveResponse.class);
    return approveResponse;
}
```

- 성공 콜백에서 받은 `pg_token`과 `tid`로 `/payment/approve` API를 호출해 결제를 확정합니다.
- Seat Controller는 승인 응답의 `tid`로 `PurchaseResponseDTO`를 조회하여 이미 선점해둔 좌석 정보를 꺼내고, 티켓 서비스로 전달합니다.

### 3. 굿즈 결제/환불 (Merch 서비스)

Merch 서비스 역시 동일한 KakaoPay 서비스 로직을 구현하며, 환불 시 `/payment/cancel` API를 호출하여 굿즈 재고를 복구합니다.

## 좌석 구매 전체 프로세스

```
[사용자]
  │ 1. 좌석 선택 요청 (JWT + pay=true/false)
  ▼
Seat 서비스
  │ 2. Redis 락 획득 → 좌석 가격/상태 재검증
  │ 3-a. pay=true  → KakaoPay Ready 호출, TID 저장, 결제 URL 응답
  │ 3-b. pay=false → 내부 결제 시뮬레이션, 티켓 서버 즉시 호출
  ▼
Ticket 서비스
  │ 4. `sendPurchaseRequest`로 좌석 정보 전달
  │ 5. 좌석 상태 `reservationStatus=YES`로 갱신
  ▼
사용자
  │ 6. 결제 완료 후 KakaoPay 승인 콜백 → 티켓 발급 확정
```

- `sendPurchaseRequest`는 좌석 정보를 티켓 서비스 API로 전송하고 성공 시 `confirmSeatPurchase`에서 좌석 예약 상태를 업데이트합니다.
- 승인 이후 Ticket 서비스가 실제 티켓을 생성하며, 필요 시 Merch 서비스와 연계해 번들 상품을 제안할 수 있습니다.

## 로컬 실행 가이드

각 서비스는 독립 Gradle 프로젝트입니다. 공통적으로 Java 17, Spring Boot, Gradle Wrapper를 사용합니다.

```bash
# 예) Auth 서비스 실행
cd BE-AUTH
./gradlew bootRun
```

