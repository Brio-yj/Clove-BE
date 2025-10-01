package com.example.msaeventinformation.service;

import com.example.msaeventinformation.exception.DuplicateEventException;
import com.example.msaeventinformation.model.Description;
import com.example.msaeventinformation.model.Event;
import com.example.msaeventinformation.model.Merch;
import com.example.msaeventinformation.model.SeatAndPrice;
import com.example.msaeventinformation.repository.EventRepository;
import com.example.msaeventinformation.repository.SeatAndPriceRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import lombok.*;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
@Slf4j
public class EventService {

    private final EventRepository eventRepository;
    private final SeatAndPriceRepository seatAndPriceRepository;
    private final S3Service s3Service;
    private final ExternalService externalService;

    @Value("${NAMESPACE}")
    private String NAMESPACE;

    public EventService(EventRepository eventRepository, SeatAndPriceRepository seatAndPriceRepository,
                        S3Service s3Service, ExternalService externalService) {
        this.eventRepository = eventRepository;
        this.seatAndPriceRepository = seatAndPriceRepository;
        this.s3Service = s3Service;
        this.externalService = externalService;
    }

    public List<Event> getAllEventsByNamespace(String namespace) {
        return eventRepository.findByNamespace(namespace);
    }

    public Event createEvent(Event event, MultipartFile eventImage,
                             MultipartFile descriptionImage,
                             MultipartFile[] merchImages,
                             Long memberId, String memberEmail, String authToken) {

        eventRepository.findByName(event.getName()).ifPresent(existingEvent -> {
            // Delete the existing event
            eventRepository.delete(existingEvent);
            log.info("Deleted existing event with name: " + event.getName());
        });

        event.setMemberId(memberId);
        event.setMemberEmail(memberEmail);
        event.setNamespace(NAMESPACE);

        if (eventImage != null && !eventImage.isEmpty()) {
            try {
                String eventImageUrl = s3Service.uploadFile(eventImage);
                event.setImage(eventImageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload event image", e);
            }
        }

        if (descriptionImage != null && !descriptionImage.isEmpty()) {
            try {
                String descriptionImageUrl = s3Service.uploadFile(descriptionImage);
                if (event.getDescription() != null) {
                    event.getDescription().setImage(descriptionImageUrl);
                }
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload description image", e);
            }
        }

        if (event.getSeatsAndPrices() != null) {
            event.getSeatsAndPrices().forEach(seatAndPrice -> seatAndPrice.setEvent(event));
        }

        List<Merch> merches = event.getMerches();
        if (merches != null && merchImages != null) {
            int minCount = Math.min(merches.size(), merchImages.length);
            for (int i = 0; i < minCount; i++) {
                Merch merch = merches.get(i);
                MultipartFile imageFile = merchImages[i];

                if (imageFile != null && !imageFile.isEmpty()) {
                    try {
                        String imageUrl = s3Service.uploadFile(imageFile);
                        log.info("merch image url {}", imageUrl);
                        merch.setImage(imageUrl);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to upload merch image", e);
                    }
                }
            }
            event.setMerches(merches);
        }else{
            log.info("merch image is empty");
        }

        Event savedEvent = eventRepository.save(event);

        log.info("seat 전송 준비");
        // SeatAndPrice 정보 업데이트 및 외부서버 전송
        if (event.getSeatsAndPrices() != null && !event.getSeatsAndPrices().isEmpty()) {
            // 전체 이벤트에 대한 일반적인 정보를 로그로 남김
            log.info("Processing event: {}", event.getName());

            for (SeatAndPrice seatAndPrice : event.getSeatsAndPrices()) {
                // 각 좌석과 가격에 대해 세부 정보 로깅
                log.info("Processing SeatAndPrice - Section: {}, Price: {}, Count: {}",
                        seatAndPrice.getSection(), seatAndPrice.getPrice(), seatAndPrice.getCount());

                externalService.sendSeatAndPriceInfo(seatAndPrice, authToken);
            }
        }


        // 각 Merch에 대해 sendMerchInfo 호출
        if (merches != null) {
            for (Merch merch : merches) {
                externalService.sendMerchInfo(merch, event.getName(), authToken);
            }
        }

        return savedEvent;
    }

    public Event updateEventByName(String name, Event eventDetails, MultipartFile image,
                                   MultipartFile descriptionImage, MultipartFile[] merchImages,
                                   Long memberId, String memberEmail, String authToken) {
        // 기존 이벤트 찾기
        Event event = eventRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("No event with name: " + name));

        // 수정 권한 검증
        if (!event.getMemberId().equals(memberId)) {
            throw new RuntimeException("No permission");
        }

        // 필드 업데이트
        if (eventDetails.getName() != null) event.setName(eventDetails.getName());
        if (eventDetails.getCast() != null) event.setCast(eventDetails.getCast());
        if (eventDetails.getVenue() != null) event.setVenue(eventDetails.getVenue());
        if (eventDetails.getEventTime() != null) event.setEventTime(eventDetails.getEventTime());
        if (eventDetails.getStartDate() != null) event.setStartDate(eventDetails.getStartDate());
        if (eventDetails.getEndDate() != null) event.setEndDate(eventDetails.getEndDate());
        if (eventDetails.getBookingStartDate() != null) event.setBookingStartDate(eventDetails.getBookingStartDate());
        if (eventDetails.getBookingEndDate() != null) event.setBookingEndDate(eventDetails.getBookingEndDate());

        // 이미지 업로드 (이벤트 이미지)
        if (image != null && !image.isEmpty()) {
            try {
                String eventImageUrl = s3Service.uploadFile(image);
                event.setImage(eventImageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload event image", e);
            }
        }

        // 설명 이미지 업로드 및 설정
        if (descriptionImage != null && !descriptionImage.isEmpty()) {
            try {
                String descriptionImageUrl = s3Service.uploadFile(descriptionImage);
                if (event.getDescription() == null) {
                    event.setDescription(new Description());
                }
                event.getDescription().setImage(descriptionImageUrl);
            } catch (IOException e) {
                throw new RuntimeException("Failed to upload description image", e);
            }
        }

        // 설명 텍스트 업데이트
        if (eventDetails.getDescription() != null && eventDetails.getDescription().getText() != null) {
            if (event.getDescription() == null) {
                event.setDescription(new Description());
            }
            event.getDescription().setText(eventDetails.getDescription().getText());
        }

        // Merch 정보 업데이트 및 전송
        List<Merch> updatedMerches = eventDetails.getMerches();
        if (updatedMerches != null && merchImages != null) {
            int minCount = Math.min(updatedMerches.size(), merchImages.length);
            for (int i = 0; i < minCount; i++) {
                Merch merch = updatedMerches.get(i);
                MultipartFile imageFile = merchImages[i];

                merch.setEvent(event);

                if (imageFile != null && !imageFile.isEmpty()) {
                    try {
                        String merchImageUrl = s3Service.uploadFile(imageFile);
                        merch.setImage(merchImageUrl);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to upload merch image", e);
                    }
                }

                // Merch 정보 외부 서버 전송
                externalService.sendMerchInfo(merch, event.getName(), authToken);
            }
            event.setMerches(updatedMerches);
        }

        log.info("seat 전송 준비");
        // SeatAndPrice 정보 업데이트 및 외부서버 전송
        if (eventDetails.getSeatsAndPrices() != null && !eventDetails.getSeatsAndPrices().isEmpty()) {
            // 전체 이벤트에 대한 일반적인 정보를 로그로 남김
            log.info("Processing event: {}", eventDetails.getName());

            for (SeatAndPrice seatAndPrice : eventDetails.getSeatsAndPrices()) {
                // 각 좌석과 가격에 대해 세부 정보 로깅
                log.info("Processing SeatAndPrice - Section: {}, Price: {}, Count: {}",
                        seatAndPrice.getSection(), seatAndPrice.getPrice(), seatAndPrice.getCount());

                externalService.sendSeatAndPriceInfo(seatAndPrice, authToken);
            }
        }

        // 저장 및 반환
        return eventRepository.save(event);
    }



    public Optional<Event> getEventByName(String name) {
        return eventRepository.findByName(name);
    }
    private void sendSeatAndPricesToExternalService(Event event, String authToken) {
        if (event.getSeatsAndPrices() != null) {
            event.getSeatsAndPrices().forEach(seat ->
                    externalService.sendSeatAndPriceInfo(seat, authToken)
            );
        }
    }
    private void uploadJsonFileIfNotEmpty(MultipartFile file, Consumer<String> setUrlFunction, String errorMessage) {
        if (file != null && !file.isEmpty()) {
            try {
                String jsonFileUrl = s3Service.uploadJsonFile(file);
                setUrlFunction.accept(jsonFileUrl);
            } catch (IOException e) {
                throw new RuntimeException(errorMessage, e);
            }
        }
    }
    private void updateEventDetails(Event event, Event eventDetails) {
        event.getSeatsAndPrices().clear();
        event.getSeatsAndPrices().addAll(eventDetails.getSeatsAndPrices());
        event.getSeatsAndPrices().forEach(seatAndPrice -> seatAndPrice.setEvent(event));

        event.setName(eventDetails.getName());
        event.setCast(eventDetails.getCast());
        event.setVenue(eventDetails.getVenue());
        event.setEventTime(eventDetails.getEventTime());
        event.setStartDate(eventDetails.getStartDate());
        event.setEndDate(eventDetails.getEndDate());
        event.setBookingStartDate(eventDetails.getBookingStartDate());
        event.setBookingEndDate(eventDetails.getBookingEndDate());
    }

}
