package org.example;

import org.example.grpc.fds.FdsVerifyRequest;
import org.example.grpc.fds.FdsVerifyResponse;
import org.example.service.FdsGrpcServiceImpl;
import org.example.service.RedisTimeSeriesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import io.grpc.stub.StreamObserver;

import java.util.concurrent.atomic.AtomicReference;

import java.time.Duration;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeSeriesRedisTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> transactions;

    @InjectMocks
    private RedisTimeSeriesService redisService;

    private static final String USER_ID = "timeseries-test-user";
    private static final String KEY = "banking:user:tx:sent:" + USER_ID;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForZSet()).thenReturn(transactions);
    }

   
    @Test
    void shouldRecordTransactionsInTimeOrder() {
        System.out.println("\n========== [TEST 1] Transaction Time Order ==========");

        redisService.recordTransaction(USER_ID, "TX001", 100.0);
        redisService.recordTransaction(USER_ID, "TX002", 200.0);
        redisService.recordTransaction(USER_ID, "TX003", 300.0);

        ArgumentCaptor<String> values =
                ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> scores =
                ArgumentCaptor.forClass(Double.class);

        verify(transactions, times(3))
                .add(eq(KEY), values.capture(), scores.capture());

        List<String> recordedValues = values.getAllValues();
        List<Double> recordedScores = scores.getAllValues();

        for (int i = 0; i < recordedValues.size(); i++) {
            System.out.printf(
                    "Transaction: %-15s | Timestamp: %.0f%n",
                    recordedValues.get(i),
                    recordedScores.get(i)
            );
        }

        assertEquals(
                List.of("TX001:100.0", "TX002:200.0", "TX003:300.0"),
                recordedValues
        );

        assertTrue(recordedScores.get(0) <= recordedScores.get(1));
        assertTrue(recordedScores.get(1) <= recordedScores.get(2));

        System.out.println("[PASS] 3 transactions recorded in timestamp order");
    }

    @Test
    void shouldRemoveTransactionsOlderThan24Hours() {
        System.out.println("\n========== [TEST 2] 24-Hour Window Cleanup ==========");

        redisService.recordTransaction(USER_ID, "NEW_TX", 200.0);

        ArgumentCaptor<Double> score =
                ArgumentCaptor.forClass(Double.class);

        verify(transactions)
                .add(eq(KEY), eq("NEW_TX:200.0"), score.capture());

        double timestamp = score.getValue();
        double cutoff = timestamp - Duration.ofHours(24).toMillis();

        verify(transactions).removeRangeByScore(
                eq(KEY),
                eq(0.0),
                eq(cutoff)
        );

        System.out.printf("New transaction timestamp: %.0f%n", timestamp);
        System.out.printf("24-hour cutoff timestamp:  %.0f%n", cutoff);
        System.out.println("Verified Redis removeRangeByScore() invocation");

        System.out.println("[PASS] 24-hour cleanup command verified");
    }

    @Test
    void shouldSet24HourTTL() {
        System.out.println("\n========== [TEST 3] Redis TTL ==========");

        redisService.recordTransaction(USER_ID, "TX001", 100.0);

        verify(redisTemplate).expire(
                KEY,
                Duration.ofHours(24)
        );

        System.out.println("Redis Key: " + KEY);
        System.out.println("Configured TTL: 24 hours (86400 seconds)");

        System.out.println("[PASS] Redis TTL configuration verified");
    }


    
        @Test
        void shouldDetectAbnormalTransaction() {

                System.out.println("\n========== [TEST 4] FDS Abnormal Transaction Detection ==========");

                // 1. 과거 거래 이력 Mock 설정
                when(transactions.count(
                        eq(KEY), anyDouble(), anyDouble()
                )).thenReturn(3L);

                when(transactions.rangeByScore(
                        eq(KEY), anyDouble(), anyDouble()
                )).thenReturn(java.util.Set.of(
                        "TX001:100.0",
                        "TX002:200.0",
                        "TX003:300.0"
                ));

                System.out.println("[MOCK] Historical transaction count: 3");
                System.out.println("[MOCK] TX001: 100.0");
                System.out.println("[MOCK] TX002: 200.0");
                System.out.println("[MOCK] TX003: 300.0");
                System.out.println("[MOCK] Historical total amount: 600.0");

                // 2. FDS 요청 생성
                FdsGrpcServiceImpl grpcService =
                        new FdsGrpcServiceImpl(redisService);

                FdsVerifyRequest request = FdsVerifyRequest.newBuilder()
                        .setUserId(USER_ID)
                        .setAccountId(1L)
                        .setAmount(1_000_000.0)
                        .build();

                System.out.println("\n[FDS REQUEST]");
                System.out.println("User ID: " + request.getUserId());
                System.out.println("Account ID: " + request.getAccountId());
                System.out.println("Requested Amount: " + request.getAmount());

                // 3. FDS 검증 결과 수집
                AtomicReference<FdsVerifyResponse> result =
                        new AtomicReference<>();

                AtomicReference<Throwable> error =
                        new AtomicReference<>();

                grpcService.verifyTransaction(
                        request,
                        new StreamObserver<FdsVerifyResponse>() {

                                @Override
                                public void onNext(FdsVerifyResponse response) {
                                result.set(response);
                                }

                                @Override
                                public void onError(Throwable t) {
                                error.set(t);
                                }

                                @Override
                                public void onCompleted() {
                                System.out.println("[gRPC] Response completed");
                                }
                        }
                );

                // 4. 응답 검증
                assertNull(error.get(), "FDS error: " + error.get());
                assertNotNull(result.get(), "FDS response is null");

                FdsVerifyResponse response = result.get();

                System.out.println("\n[FDS RESPONSE]");
                System.out.println("Allowed: " + response.getAllowed());
                System.out.println("Message: " + response.getMessage());

                // 5. 이상거래 차단 검증
                assertFalse(
                        response.getAllowed(),
                        "Abnormal transaction should be blocked"
                );

                assertEquals(
                        "이번 거래를 포함하면 시간당 누적 금액 초과",
                        response.getMessage()
                );

                System.out.println("\n[PASS] Abnormal transaction blocked successfully");
                System.out.println("[PASS] FDS rejection reason verified");
                System.out.println("======================================================\n");
        }




}
