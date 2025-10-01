package com.example.bemsaseat.auth.service;


import com.example.bemsaseat.auth.dto.MemberResponseDto;
import com.example.bemsaseat.auth.jwt.TokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final TokenProvider tokenProvider;

    @Transactional(readOnly = true)
    public MemberResponseDto getUserInfoFromToken(String token) {
        if (!tokenProvider.validateToken(token)) {
            throw new RuntimeException("Invalid Token");
        }

        Long memberId = tokenProvider.getMemberIdFromToken(token);
        String email = tokenProvider.getEmailFromToken(token);
        String authority = tokenProvider.getAuthorityFromToken(token);

        return MemberResponseDto.of(memberId, email, authority);
    }
}