package com.example.msaauth.entity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
public class RefreshToken {

    @Id
    @Column(name = "rt_key")
    private String key;

    @Column(name = "rt_value")
    private String value;

    @Column(name = "member_id")
    private Long memberId;
    @Builder
    public RefreshToken(String key, String value,Long memberId) {
        this.key = key;
        this.value = value;
        this.memberId = memberId;
    }

    public RefreshToken updateValue(String token) {
        this.value = token;
        return this;
    }
}