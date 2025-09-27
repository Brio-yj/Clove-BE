package com.example.seat;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class RedisLockRepository {
    private final StringRedisTemplate redisTemplate;
    private static final String KEY_PREFIX = "lock:seat:";

    public boolean lock(Long seatId) {
        return Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + seatId, "locked", Duration.ofSeconds(3)));
    }

    public void unlock(Long seatId) {
        redisTemplate.delete(KEY_PREFIX + seatId);
    }
}
