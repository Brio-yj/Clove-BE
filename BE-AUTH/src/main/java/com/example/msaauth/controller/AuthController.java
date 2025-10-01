package com.example.msaauth.controller;

import com.example.msaauth.dto.LoginDto;
import com.example.msaauth.dto.MemberRequestDto;
import com.example.msaauth.dto.MemberResponseDto;
import com.example.msaauth.dto.TokenDto;
import com.example.msaauth.dto.TokenResponseDto;
import com.example.msaauth.entity.Member;
import com.example.msaauth.exception.DuplicateEmailException;
import com.example.msaauth.repository.MemberRepository;
import com.example.msaauth.service.AuthService;
import com.example.msaauth.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final MemberRepository memberRepository;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody MemberRequestDto memberRequestDto) {
        try {
            MemberResponseDto response = authService.signup(memberRequestDto);
            return ResponseEntity.ok(response);
        } catch (DuplicateEmailException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginDto loginDto, HttpServletResponse response) {
        try {
            TokenResponseDto tokenResponse = authService.login(loginDto);

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + tokenResponse.getAccessToken());
            CookieUtil.addRefreshTokenToCookie(response, tokenResponse.getRefreshToken());

            Member member = memberRepository.findByEmail(loginDto.getEmail())
                    .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
            MemberResponseDto userResponse = MemberResponseDto.of(member);

            return ResponseEntity.ok().headers(headers).body(userResponse);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestBody MemberRequestDto memberRequestDto) {
        authService.logout(memberRequestDto);
        String responseMessage = memberRequestDto.getEmail() + " logout 완료";
        return ResponseEntity.ok(responseMessage);
    }

    @PostMapping("/reissue")
    public ResponseEntity<String> reissue(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = CookieUtil.extractTokenFromCookie(request, "refreshToken")
                .orElseThrow(() -> new RuntimeException("Refresh Token이 존재하지 않습니다."));

        TokenDto tokenDto = authService.reissue(refreshToken);

        // 새로운 Access 토큰을 Authorization 헤더에 저장
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDto.getAccessToken());

        // Refresh 토큰을 HttpOnly 쿠키에 저장
        CookieUtil.addRefreshTokenToCookie(response, tokenDto.getRefreshToken());

        String successMessage = "Access,Refresh 토큰이 정상적으로 발급되었습니다.";

        return ResponseEntity.ok().headers(headers).body(successMessage);
    }
}