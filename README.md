# Clove BE (티켓 커머스 MSA)

> 공연 티켓팅과 굿즈 판매를 지원하는 Clove 백엔드 모놀리포. Auth, Event, Seat, Ticket, Merch 서비스로 구성된 MSA 예제 프로젝트입니다. 각 모듈은 독립적으로 배포 가능하며, JWT 기반 인증과 Redis 분산락으로 좌석 선점 충돌을 제어합니다.

## 전체 아키텍처

```
[Client]
   │
   ├──▶ API Gateway (외부 구성)
   │        │
   │        ├──▶ Auth 서비스 ──┐  (회원 가입/로그인, JWT 발급)
   │        ├──▶ Event 서비스 ─┼──▶ Seat 서비스 (좌석 조회·선점)
   │        ├──▶ Merch 서비스 ─┤
   │        └──▶ Ticket 서비스 ─┘
   │
   └──▶ Kafka · Redis · MySQL · S3 (공유 인프라)
```

- **Auth**: 회원 인증과 토큰 발급, 카프카로 이메일 알림 전송.
- **Event**: 판매자가 이벤트 정보를 등록/수정하고 S3에 이미지 자산을 관리.
- **Seat**: 공연 좌석 조회와 카카오페이/내부 결제 연동, Redis 락으로 중복 선점 방지.
- **Ticket**: 좌석 구매 확정 후 실제 티켓 정보를 생성/저장.
- **Merch**: 공연 관련 굿즈를 조회/구매하는 도메인.

## 핵심 기능 요약

| 도메인 | 핵심 기능 | 연관 클래스 |
| --- | --- | --- |
| Auth | JWT Access/Refresh 토큰 발급·검증, Refresh 토큰 저장 및 재발급 | [`TokenProvider`](BE-AUTH/src/main/java/com/example/msaauth/jwt/TokenProvider.java), [`AuthService`](BE-AUTH/src/main/java/com/example/msaauth/service/AuthService.java) |
| Event | 이벤트 등록/수정, S3 이미지 업로드, 판매자 인증 | [`EventController`](BE-EVENT/src/main/java/com/example/msaeventinformation/controller/EventController.java), [`EventService`](BE-EVENT/src/main/java/com/example/msaeventinformation/service/EventService.java) |
| Seat | 좌석 검증, 카카오페이 결제 준비, Redis 분산락 기반 좌석 선점 | [`SeatService`](BE-SEAT/BE-SEAT-YES-NUMBER-YES-KAKAO/src/main/java/com/example/bemsaseat/seat/service/SeatService.java), [`RedisLockRepository`](BE-SEAT/BE-SEAT-YES-NUMBER-NO-KAKAO/src/main/java/com/example/seat/RedisLockRepository.java) |
| Ticket | 구매 완료 좌석으로 티켓 생성, 인증 필터 적용 | [`TicketService`](BE-TICKET/BE-TICKET-NO-KAKAO/src/main/java/com/example/bemsaticket/ticket/service/TicketService.java) |
| Merch | 굿즈 조회/결제, 인증 필터 공유 | [`MerchService`](BE-MERCH/BE-MERCH-NO-KAKAO/src/main/java/com/example/bemerch/merch/service/MerchService.java) |

## JWT 인증 흐름

1. **로그인 요청** – `AuthService.login`이 입력한 이메일/비밀번호를 인증하고 `TokenProvider`로 Access/Refresh 토큰을 생성합니다.
2. **토큰 응답** – Access 토큰은 사용자 권한, 이메일, 멤버 ID를 담고 100분 동안 유효합니다. Refresh 토큰은 7일 TTL로 DB(`RefreshTokenRepository`)에 저장합니다.
3. **요청 보호** – 각 도메인 서비스는 공통 `JwtAuthenticationFilter`를 통해 헤더의 Access 토큰을 파싱, `TokenProvider`로 유저를 복원하고 `SecurityContext`에 주입합니다.
4. **재발급/로그아웃** – Refresh 토큰 검증 후 새 Access 토큰을 발급하거나, 로그아웃 시 저장된 Refresh 토큰을 삭제합니다.

> Access 토큰 Payload에는 `memberEmail`, `memberAuthority`, `memberId`가 포함되어 있어 마이크로서비스 간 유저 컨텍스트 공유가 용이합니다.

## Redis 좌석 선점 전략

실시간 좌석 예매에서는 동일 좌석을 동시에 선택하는 시나리오가 잦기 때문에 Seat 서비스는 Redis 기반 분산 락을 제공합니다.

1. **락 획득** – `RedisLockRepository.lock(seatId)`가 `lock:seat:{seatId}` 키를 `SETNX`로 생성해 3초 TTL을 부여합니다. 이미 존재하면 예외를 던져 중복 선점을 차단합니다.
2. **좌석 검증** – 락을 획득한 요청만 실제 좌석 상태를 조회/검증(`SeatRepository.findBy...`)하고 결제 준비로 진입합니다.
3. **락 해제** – 결제 성공/실패와 관계없이 `finally` 블록에서 `unlock(seatId)`로 키를 삭제하여 다른 사용자가 다시 시도할 수 있습니다.

결과적으로 동일 좌석에 대한 경쟁 요청은 직렬화되어, **중복 결제/티켓 발급**을 방지합니다.

## 좌석 구매 프로세스

```
[사용자]
  │ 1. 좌석 선택 요청 (JWT 포함)
  ▼
Seat 서비스
  │ 2. Redis 락 획득 + 좌석 검증
  │ 3-a. (pay=true) KakaoPay Ready API 호출 → TID 발급
  │ 3-b. (pay=false) 내부 결제 시뮬레이션
  ▼
Ticket 서비스
  │ 4. `sendPurchaseRequest`로 좌석 예약 정보 전달
  │ 5. 좌석 상태를 `reservationStatus=YES`로 갱신
  ▼
사용자
  │ 6. 결제 URL 또는 예약 결과 응답
```

- 모든 단계는 JWT 토큰에서 추출한 사용자 정보로 추적됩니다.
- 결제가 완료되면 Ticket 서비스가 구매 내역을 저장하고, 필요 시 Merch 서비스와 연동해 번들 상품을 제안할 수 있습니다.

## 로컬 실행 가이드

각 서비스는 독립 Gradle 프로젝트입니다. 공통적으로 Java 17, Spring Boot, Gradle Wrapper를 사용합니다.

```bash
# 예) Auth 서비스 실행
cd BE-AUTH
./gradlew bootRun
```

Redis, Kafka, MySQL, S3 등 외부 의존성은 별도로 구성해야 하며, `application.properties`에서 호스트 정보를 수정합니다.

## 개발 노트

- **Kafka**: 회원 가입 이벤트를 발행하고, 실패한 이메일 전송은 `email-retry` 토픽을 구독하는 `EmailRetryConsumer`에서 재시도합니다.
- **S3 통합**: Event 서비스는 공연 이미지/설명/머천다이즈 이미지를 S3에 업로드하고, URL을 저장합니다.
- **모듈 분리**: 좌석/티켓/머천다이즈 서비스는 공통 JWT 설정을 공유하지만 도메인 로직은 각자의 패키지로 격리되어 있습니다.

## 디렉터리 구조

```
Clove-BE/
├── BE-AUTH/        # 인증 서비스 (JWT, Kafka)
├── BE-EVENT/       # 공연 이벤트 관리 (S3 업로드)
├── BE-SEAT/        # 좌석 서비스 (Redis 락, 결제 연동)
├── BE-TICKET/      # 티켓 발급 서비스
└── BE-MERCH/       # 굿즈 서비스
```

## 참고 링크

- [mini-spring-boot 예시 README](https://github.com/MilkTea24/mini-spring-boot)
