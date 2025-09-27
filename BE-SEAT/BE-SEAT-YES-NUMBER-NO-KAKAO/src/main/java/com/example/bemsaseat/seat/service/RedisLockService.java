package com.example.bemsaseat.seat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisLockService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setScriptText("if redis.call('get', KEYS[1]) == ARGV[1] then " +
                "return redis.call('del', KEYS[1]) else return 0 end");
    }

    private final StringRedisTemplate redisTemplate;

    @Value("${seat.lock.ttl-seconds:15}")
    private long lockTtlSeconds;

    @Value("${seat.lock.wait-millis:500}")
    private long lockWaitMillis;

    public String acquireLock(String key) {
        String token = UUID.randomUUID().toString();
        long deadline = System.currentTimeMillis() + lockWaitMillis;

        while (System.currentTimeMillis() <= deadline) {
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(key, token, Duration.ofSeconds(lockTtlSeconds));

            if (Boolean.TRUE.equals(locked)) {
                log.debug("Acquired redis lock for key={} with token={}", key, token);
                return token;
            }

            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Lock acquisition interrupted", e);
            }
        }

        throw new IllegalStateException("좌석 잠금 획득에 실패했습니다. 잠시 후 다시 시도해주세요.");
    }

    public void releaseLock(String key, String token) {
        try {
            Long result = redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
            if (result == null || result == 0L) {
                log.warn("Failed to release redis lock for key={} with token={}", key, token);
            } else {
                log.debug("Released redis lock for key={} with token={}", key, token);
            }
        } catch (DataAccessException e) {
            log.error("Redis error while releasing lock for key={}: {}", key, e.getMessage());
        }
    }
}
