package com.example.seat;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SeatService {
    private final RedisLockRepository lockRepository;

    @Transactional
    public void reserveSeat(Long seatId, Long userId) {
        if (!lockRepository.lock(seatId)) {
            throw new IllegalStateException("Seat already reserved");
        }
        try {
            // seat reservation logic would go here
        } finally {
            lockRepository.unlock(seatId);
        }
    }
}
