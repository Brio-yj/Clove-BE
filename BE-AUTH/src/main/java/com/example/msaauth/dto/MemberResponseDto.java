package com.example.msaauth.dto;

import com.example.msaauth.entity.Authority;
import com.example.msaauth.entity.Member;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MemberResponseDto {
    private String email;
    private Authority authority;

    public static MemberResponseDto of(Member member) {
        return new MemberResponseDto(member.getEmail(),member.getAuthority());
    }
}