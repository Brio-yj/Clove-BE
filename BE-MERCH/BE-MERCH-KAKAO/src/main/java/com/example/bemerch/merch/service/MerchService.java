package com.example.bemerch.merch.service;


import com.example.bemerch.auth.jwt.TokenProvider;
import com.example.bemerch.kakao.KakaoPayService;
import com.example.bemerch.kakao.dto.KakaoApproveResponse;
import com.example.bemerch.kakao.dto.KakaoCancelResponse;
import com.example.bemerch.kakao.dto.KakaoReadyResponse;
import com.example.bemerch.kakao.dto.PurchaseResponseDTO;
import com.example.bemerch.merch.config.RandomStringGenerator;
import com.example.bemerch.merch.dto.*;
import com.example.bemerch.merch.dto.purchase.MerchPurchaseDetail;
import com.example.bemerch.merch.dto.purchase.MerchPurchaseRequest;
import com.example.bemerch.merch.dto.refund.MerchRefundDetail;
import com.example.bemerch.merch.dto.refund.MerchRefundRequest;
import com.example.bemerch.merch.model.Merch;
import com.example.bemerch.merch.repository.MerchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.example.bemerch.merch.config.RandomStringGenerator.generateRandomString;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchService {

    private final MerchRepository merchRepository;
    private final KakaoPayService kakaoPayService;
    private final TokenProvider tokenProvider;


    @Value("${pay}")
    private Boolean pay;

    public List<Merch> getAllMerch() {
        return merchRepository.findAll();
    }

    public List<Merch> getMerchByEmail(String email) {
        return merchRepository.findByEmail(email);
    }

    public void saveMerches(List<ReceiveMerch> receiveMerches) {
        for (ReceiveMerch dto : receiveMerches) {
            // Merch 객체 생성
            for (int i = 1; i <= dto.getCount(); i++) {
                // Merch 객체 생성
                Merch merch = new Merch();
                merch.setName(dto.getName());
                merch.setMid(generateRandomString(16));
                merch.setPrice(dto.getPrice());
                merch.setImage(dto.getImage());
                merch.setEventName(dto.getEventName());

                merchRepository.save(merch);
            }
        }
    }

    public PurchaseResponseDTO purchaseMerches(MerchPurchaseRequest merchPurchaseRequest, String email, String jwtToken) {
        log.info("Starting purchase process for email: {}", email);
        boolean allAvailable = true;
        List<MinimalMerchDTO> minimalMerchDTOList = new ArrayList<>();
        Map<String, Integer> purchaseQuantityMap = new HashMap<>();

        // 클라이언트의 요청을 기반으로 상품 수량 집계, 이름에 : 불가
        for (MerchPurchaseDetail merchPurchaseDetail : merchPurchaseRequest.getMerches()) {
            String key = merchPurchaseDetail.getName() + ":" + merchPurchaseDetail.getPrice() + ":" + merchPurchaseDetail.getEventName();
            purchaseQuantityMap.put(key, purchaseQuantityMap.getOrDefault(key, 0) + 1);
        }
        log.info("Purchase quantity map: {}", purchaseQuantityMap);

        // 상품 검증 수행
        for (Map.Entry<String, Integer> entry : purchaseQuantityMap.entrySet()) {
            String[] merchDetails = entry.getKey().split(":");
            String name = merchDetails[0];
            log.info("name: {}", name);
            int price = Integer.parseInt(merchDetails[1]);
            log.info("price: {}", price);
            String eventName = merchDetails[2];
            log.info("eventName: {}", eventName);
            int requestedQuantity = entry.getValue(); // 요청받은 구매 수량
            log.info("requestedQuantity: {}", requestedQuantity);

            //동일상품 여러개니까 있는지 없는지 확인만 하면 된다
            if (merchRepository.existsByNameAndPriceAndEventName(name, price, eventName)) {
                log.info("merch already exists");
                // 재고 수량 확인
                int totalMerchCount = merchRepository.countByNameAndPriceAndEventName(name, price, eventName);
                // 판매된 수량 확인
                int soldCount = merchRepository.countByNameAndPriceAndEventNameAndSoldTrue(name, price, eventName);
                if (totalMerchCount - soldCount < requestedQuantity) {
                    log.warn("상품 {} (가격: {}, 이벤트: {})의 재고가 부족합니다. 요청한 수량: {}, 사용 가능한 수량: {}",
                            name, price, eventName, requestedQuantity, totalMerchCount - soldCount);
                    allAvailable = false; // 재고 부족
                    break;
                }
            } else {
                log.info("상품 {} (가격: {}, 이벤트: {})은 존재하지 않습니다.", name, price, eventName);
                allAvailable = false;
                break;
            }
        }

        // 구매 가능 여부에 따라 처리
        if (!allAvailable) {
            return new PurchaseResponseDTO("Failure", "일부 상품이 이미 구매되었거나 존재하지 않습니다.");
        }

        // 결제 로직 처리
        if (pay) {
            log.info("모든 상품이 확인되었습니다. 결제 준비 중...");
            KakaoReadyResponse readyResponse = kakaoPayService.kakaoPayReady(merchPurchaseRequest.getMerches(), email);
            log.info("KakaoPay 결제 준비 응답을 받았습니다.");
            return new PurchaseResponseDTO(readyResponse, email, jwtToken); // 결제 결과 반환
        } else {
            log.info("KakaoPay 사용안함");

            // 카카오페이를 사용하지 않는 경우, 각 상품에 대해 판매를 처리하고 재고를 업데이트합니다.
            for (Map.Entry<String, Integer> entry : purchaseQuantityMap.entrySet()) {
                log.info("Processing purchase entry: {}", entry);

                // Split the key to extract merchandise details
                String[] merchDetails = entry.getKey().split(":");
                if (merchDetails.length < 3) {
                    log.error("Invalid key format for entry: {}. Expected format is 'name,price,eventName'", entry.getKey());
                    continue;
                }

                String name = merchDetails[0];
                int price = Integer.parseInt(merchDetails[1]);
                String eventName = merchDetails[2];
                int quantityToPurchase = entry.getValue(); // 구매할 수량
                for(int i=0;i<quantityToPurchase;i++) {
                    log.info("Attempting to purchase {} of {}, priced at {}, for event: {}", quantityToPurchase, name, price, eventName);

                    Optional<Merch> existingMerchOpt = merchRepository.findFirstByNameAndPriceAndEventNameAndSoldIsFalse(name, price, eventName);

                    if (existingMerchOpt.isPresent()) {
                        Merch existingMerch = existingMerchOpt.get();
                        log.info("Found available merch: {}", existingMerch);

                        existingMerch.setEmail(email);
                        existingMerch.setTid(generateRandomString(16)); // 16자리 랜덤 TID 생성
                        existingMerch.setSold(true);

                        // 현재 판매 상태 저장
                        merchRepository.save(existingMerch);
                        log.info("Merch updated and saved: {}", existingMerch);

                        // MinimalMerchDTO 객체 생성하여 리스트에 추가
                        MinimalMerchDTO minimalMerchDTO = new MinimalMerchDTO(
                                existingMerch.getName(),
                                existingMerch.getPrice(),
                                existingMerch.getEventName(),
                                existingMerch.getEmail(),
                                existingMerch.getTid()
                        );
                        minimalMerchDTOList.add(minimalMerchDTO);
                        log.info("Added to MinimalMerchDTO list: {}", minimalMerchDTO);
                    } else {
                        log.warn("No available merch found for name: {}, price: {}, event: {}", name, price, eventName);
                    }
                    log.info("Finished processing purchase entry for key: {}", entry.getKey());
                }
            }


            // 최종 구매 응답 생성
            PurchaseResponseDTO purchaseResponse = new PurchaseResponseDTO();
            purchaseResponse.setEmail(email);
            purchaseResponse.setMerchDTOS(minimalMerchDTOList);
            log.info("Returning booked items information: {}", purchaseResponse);
            return purchaseResponse;
        }
    }

    //purchase-앞에 비어있는거 먼저
    public List<MinimalMerchDTO> updateMerchesAndStock(PurchaseResponseDTO purchaseResponse, KakaoApproveResponse kakaoApproveResponse) {
        List<MinimalMerchDTO> minimalMerchDTOList = new ArrayList<>();

        for (MerchPurchaseDetail originalMerch : purchaseResponse.getOriginalMerches()) {
            String name = originalMerch.getName();
            int price = originalMerch.getPrice();
            String eventName = originalMerch.getEventName();

            // Merch 검색 및 업데이트
            Optional<Merch> existingMerchOpt = merchRepository.findFirstByNameAndPriceAndEventNameAndSoldIsFalse(name, price, eventName);

            if (existingMerchOpt.isPresent()) {
                // Merch의 판매 상태 업데이트
                Merch merch = existingMerchOpt.get();
                merch.setName(originalMerch.getName());
                merch.setPrice(originalMerch.getPrice());
                merch.setEventName(originalMerch.getEventName());
                merch.setEmail(kakaoApproveResponse.getPartner_user_id()); // 사용자 이메일 업데이트
                merch.setTid(kakaoApproveResponse.getTid()); // TID 업데이트
                merch.setSold(true); // 판매 상태로 설정

                // 데이터베이스에 Merch 저장 (업데이트)
                merchRepository.save(merch);
                log.info("Merch updated: {}", merch);

                // MinimalMerchDTO 생성
                MinimalMerchDTO minimalMerchDTO = new MinimalMerchDTO(
                        merch.getName(),
                        merch.getPrice(),
                        merch.getEventName(),
                        merch.getEmail(),
                        merch.getTid()
                );
            }
        }
        return minimalMerchDTOList;
    }

    public String cancelTicket(MerchRefundRequest refundRequest, String authorizationHeader) {
        log.info("Starting ticket cancellation process for request: {}", refundRequest);

        String token = authorizationHeader.substring(7);
        String email = tokenProvider.getEmailFromToken(token);
        log.debug("User email: {}", email);

        try {
            for (MerchRefundDetail merchRefundDetail : refundRequest.getMerchRefundDetails()) {
                Optional<Merch> existingMerch = merchRepository.findByNameAndPriceAndEventNameAndTidAndEmailAndMid(
                        merchRefundDetail.getName(),
                        merchRefundDetail.getPrice(),
                        merchRefundDetail.getEventName(),
                        merchRefundDetail.getTid(),
                        email,
                        merchRefundDetail.getMid()

                );

                if (!existingMerch.isPresent()) {
                    log.info("상품 {} (가격: {})은 구매된 적이 없습니다.", merchRefundDetail.getName(), merchRefundDetail.getPrice());
                    return "해당 상품을 찾을 수 없습니다.";
                }

                Merch merch = existingMerch.get();

                if (pay) {
                    log.info("Initiating Kakao Pay refund process for TID: {}", merch.getTid());
                    KakaoCancelResponse cancelResponse = kakaoPayService.kakaoCancel(refundRequest.getMerchRefundDetails());

                    if (cancelResponse == null || cancelResponse.getTid() == null) {
                        log.error("Kakao Pay refund request failed");
                        throw new IllegalStateException("카카오페이 환불 요청에 실패했습니다.");
                    }
                    log.info("Kakao Pay refund completed successfully");
                }

                // Update Merch entry instead of deletion
                merch.setEmail(null);
                merch.setTid(null);
                merch.setSold(false);
                merchRepository.save(merch);
                log.info("Merch entry updated in the database: {}", merch);
            }

            log.info("Ticket cancellation process completed successfully");
            return "티켓이 성공적으로 취소되었습니다.";
        } catch (Exception e) {
            log.error("Error occurred during ticket cancellation process", e);
            throw new RuntimeException("티켓 취소 중 오류가 발생했습니다.", e);
        }
    }


}