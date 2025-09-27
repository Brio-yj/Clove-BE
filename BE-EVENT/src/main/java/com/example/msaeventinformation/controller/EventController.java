package com.example.msaeventinformation.controller;

import com.example.msaeventinformation.auth.jwt.TokenProvider;
import com.example.msaeventinformation.exception.DuplicateEventException;
import com.example.msaeventinformation.model.Description;
import com.example.msaeventinformation.model.JsonFileInfo;
import com.example.msaeventinformation.model.Merch;
import com.example.msaeventinformation.service.S3Service;
import com.example.msaeventinformation.model.Event;
import com.example.msaeventinformation.service.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import lombok.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/event")
public class EventController {

    private final EventService eventService;
    private final TokenProvider tokenProvider;
    private final S3Service s3Service;


    @Value("${NAMESPACE}")
    private String NAMESPACE;
    //판매자,구매자 모두 사용가능 전체 이벤트 조회
//    @GetMapping
//    public List<Event> getAllEvents() {
//        return eventService.getAllEvents();
//    }

    @GetMapping
    public List<Event> getAllEventsByNamespace() {
        return eventService.getAllEventsByNamespace(NAMESPACE);
    }

    //판매자가 사용하는 이벤트 등록
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createEvent(
            @RequestPart("event") Event event,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "descriptionImage", required = false) MultipartFile descriptionImage,
            @RequestPart(value = "merchImages", required = false) MultipartFile[] merchImages,
            @RequestHeader("Authorization") String bearerToken) {

        try {
            // 토큰 추출 및 사용자 정보 가져오기
            String token = extractToken(bearerToken);
            Long memberId = tokenProvider.getMemberIdFromToken(token);
            String memberEmail = tokenProvider.getEmailFromToken(token);

            // 이벤트 생성 호출
            Event createdEvent = eventService.createEvent(event, image, descriptionImage, merchImages, memberId, memberEmail, token);
            return ResponseEntity.ok(createdEvent);
        } catch (DuplicateEventException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("이미 판매중인 공연입니다.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(null);
        }
    }


    //판매자가 사용하는 이벤트 수정
    @PutMapping(value = "/{name}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Event> updateEventByName(
            @PathVariable String name,
            @RequestPart(value = "event") Event eventDetails,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "descriptionImage", required = false) MultipartFile descriptionImage,
            @RequestPart(value = "merchImages", required = false) MultipartFile[] merchImages,
            @RequestHeader("Authorization") String bearerToken) {

        try {
            String token = extractToken(bearerToken);
            Long memberId = tokenProvider.getMemberIdFromToken(token);
            String memberEmail = tokenProvider.getEmailFromToken(token);

            Event updatedEvent = eventService.updateEventByName(name, eventDetails, image, descriptionImage, merchImages, memberId, memberEmail, token);
            return ResponseEntity.ok(updatedEvent);
        } catch (RuntimeException e) {
            e.printStackTrace();
            return ResponseEntity.notFound().build(); // Return 404 if the event is not found
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Handle other exceptions
        }
    }




    //사용안하는 api
    @GetMapping("/json-files")
    public ResponseEntity<List<JsonFileInfo>> getAllJsonFiles() {
        List<JsonFileInfo> jsonFiles = s3Service.getJsonFiles();
        return ResponseEntity.ok(jsonFiles);
    }
    @GetMapping("/json-file-url/{fileName}")
    public ResponseEntity<String> getJsonFileUrl(@PathVariable String fileName) {
        try {
            String fileUrl = s3Service.getJsonFileUrlByFileName(fileName);
            return ResponseEntity.ok(fileUrl);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
    //사용자와 관련없음
    private String extractToken(String bearerToken) {
        return bearerToken.substring(7);
    }
    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<String> handleDuplicateEventException(DuplicateEventException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

}