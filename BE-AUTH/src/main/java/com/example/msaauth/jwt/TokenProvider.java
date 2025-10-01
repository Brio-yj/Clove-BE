package com.example.msaauth.jwt;


import com.example.msaauth.dto.TokenDto;
import com.example.msaauth.entity.Member;
import com.example.msaauth.repository.MemberRepository;
import com.example.msaauth.service.AuthService;
import com.example.msaauth.service.CustomUserDetailsService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.weaver.patterns.IToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;
@Slf4j
@Component
public class TokenProvider {

    private static final String AUTHORITIES_KEY = "auth";
    private static final String BEARER_TYPE = "Bearer";
    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000 * 60 * 2000;           // 100분
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 1000 * 60 * 60 * 24 * 7;  // 7일
    private static final String MEMBER_ID_KEY = "memberId";

    private final Key key;
    private final  MemberRepository memberRepository;
    private final CustomUserDetailsService customUserDetailsService;

    private static final Logger logger = LoggerFactory.getLogger(TokenProvider.class);

    public TokenProvider(@Value("${jwt.secret}") String secretKey,
                         CustomUserDetailsService customUserDetailsService, MemberRepository memberRepository) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.memberRepository = memberRepository;
        this.customUserDetailsService=customUserDetailsService;
    }

    public TokenDto generateTokenDto(Authentication authentication) {
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        long now = (new Date()).getTime();

        Date accessTokenExpiresIn = new Date(now + ACCESS_TOKEN_EXPIRE_TIME);
        User user = (User) authentication.getPrincipal();
        Long memberId = Long.parseLong(user.getUsername()); // Username에 id 값이 있음

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Member not found"));

        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())                   // payload "sub": "name"
                .claim(AUTHORITIES_KEY, authorities)                    // payload "auth": "ROLE_USER"
                .claim(MEMBER_ID_KEY, memberId)                         // payload "memberId": "123"
                .claim("memberEmail", member.getEmail())          // payload "memberEmail": "user@example.com"
                .claim("memberAuthority", member.getAuthority().name()) // payload "memberAuthority": "CLIENT"
                .setExpiration(accessTokenExpiresIn)                    // payload "exp": 151621022 (ex)
                .signWith(key, SignatureAlgorithm.HS512)                // header "alg": "HS512"
                .compact();

        String refreshToken = Jwts.builder()
                .setSubject(String.valueOf(memberId))
                .setExpiration(new Date(now + REFRESH_TOKEN_EXPIRE_TIME))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();

        return TokenDto.builder()
                .grantType(BEARER_TYPE)
                .accessToken(accessToken)
                .accessTokenExpiresIn(accessTokenExpiresIn.getTime())
                .refreshToken(refreshToken)
                .build();
    }

    public Authentication getAuthentication(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (claims.get(AUTHORITIES_KEY) == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");
        }

        Collection<? extends GrantedAuthority> authorities =
                Arrays.stream(claims.get(AUTHORITIES_KEY).toString().split(","))
                        .map(SimpleGrantedAuthority::new)
                        .collect(Collectors.toList());

        Long memberId = claims.get(MEMBER_ID_KEY, Long.class);

        UserDetails userDetails = customUserDetailsService.loadUserByMemberId(memberId);

        return new UsernamePasswordAuthenticationToken(userDetails, "", authorities);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("잘못된 JWT 서명입니다.");
        } catch (ExpiredJwtException e) {
            log.info("만료된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.info("지원되지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.info("JWT 토큰이 잘못되었습니다.");
        }
        return false;
    }

    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }

    public Authentication getAuthenticationFromRefreshToken(String refreshToken) {
        Logger logger = LoggerFactory.getLogger(this.getClass());

        Claims claims = parseClaims(refreshToken);

        logger.info("Refresh 토큰의 클레임 정보: {}", claims.toString());

        Long memberId = Long.parseLong(claims.getSubject());

        logger.info("사용자의 멤버 아이디: {}", memberId);

        UserDetails userDetails = customUserDetailsService.loadUserByMemberId(memberId);

        return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
    }
}
