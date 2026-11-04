package org.example.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;

@Component
public class RedisTimeSeriesService {

    private final StringRedisTemplate redisTemplate;

    public RedisTimeSeriesService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 송금 발생 시 Redis ZSET에 시계열 데이터 기록
     */
    public void recordTransaction(String userId, String transactionId, double amount) {
        String key = "banking:user:tx:sent:" + userId;
        long timestamp = Instant.now().toEpochMilli(); // 현재 시간을 밀리초 Score로 사용
        
        // ZSET에 추가 (Member: "transactionId:amount", Score: 타임스탬프)
        String value = transactionId + ":" + amount;
        redisTemplate.opsForZSet().add(key, value, timestamp);

        // 슬라이딩 윈도우 관리: 24시간이 지난 데이터는 자동 삭제 (메모리 방어)
        long twentyFourHoursAgo = timestamp - (24 * 60 * 60 * 1000L);
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, twentyFourHoursAgo);

        // 키 만료 시간 24시간 설정 (휴면 유저 정리)
        redisTemplate.expire(key, java.time.Duration.ofHours(24));
    }


    public void recordIncomingTransaction(
        long receivingAccountId,  // 돈을 받은 계좌
        long senderAccountId,     // 돈을 보낸 계좌
        String transactionId,
        double amount) {

        String key = "banking:account:tx:received:" + receivingAccountId;
        long now = Instant.now().toEpochMilli();

        // 거래 ID : 금액 : 보낸 계좌 ID
        String value = transactionId + ":" + amount + ":" + senderAccountId;

        redisTemplate.opsForZSet().add(key, value, now);

        // 기존 코드와 같이 24시간 보관
        long cutoff = now - java.time.Duration.ofHours(24).toMillis();
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, cutoff);
        redisTemplate.expire(key, java.time.Duration.ofHours(24));
    }
    /**
     * 최근 N 시간 동안의 송금 횟수 및 누적 금액 조회 (시계열 윈도우 분석)
     */
    public WindowAnalysisResult getRecentWindowStats(String userId, long windowMillis) {
        String key = "banking:user:tx:sent:" + userId;
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowMillis;

        // 1. 지정된 시간 범위(windowStart ~ now) 내의 데이터 개수(횟수) 조회
        Long count = redisTemplate.opsForZSet().count(key, windowStart, now);
        long txCount = (count != null) ? count : 0;

        // 2. 해당 범위의 모든 값(Member)을 가져와 누적 금액 계산
        Set<String> recentTxs = redisTemplate.opsForZSet().rangeByScore(key, windowStart, now);
        double totalAmount = 0.0;
        
        if (recentTxs != null) {
            for (String tx : recentTxs) {
                // "transactionId:amount" 형태로 저장되어 있으므로 파싱
                String[] parts = tx.split(":");
                if (parts.length == 2) {
                    totalAmount += Double.parseDouble(parts[1]);
                }
            }
        }

        return new WindowAnalysisResult(txCount, totalAmount);
    }

  
    public record WindowAnalysisResult(long count, double totalAmount) {}
}