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
        redisService.recordTransaction(USER_ID, "TX001", 100.0);
        redisService.recordTransaction(USER_ID, "TX002", 200.0);
        redisService.recordTransaction(USER_ID, "TX003", 300.0);

        ArgumentCaptor<String> values = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Double> scores = ArgumentCaptor.forClass(Double.class);
        verify(transactions, times(3)).add(eq(KEY), values.capture(), scores.capture());

        assertEquals(List.of("TX001:100.0", "TX002:200.0", "TX003:300.0"), values.getAllValues());
        assertTrue(scores.getAllValues().get(0) <= scores.getAllValues().get(1));
        assertTrue(scores.getAllValues().get(1) <= scores.getAllValues().get(2));
    }

    @Test
    void shouldRemoveTransactionsOlderThan24Hours() {
        redisService.recordTransaction(USER_ID, "NEW_TX", 200.0);

        ArgumentCaptor<Double> score = ArgumentCaptor.forClass(Double.class);
        verify(transactions).add(eq(KEY), eq("NEW_TX:200.0"), score.capture());
        verify(transactions).removeRangeByScore(
                eq(KEY),
                eq(0.0),
                eq(score.getValue() - Duration.ofHours(24).toMillis())
        );
    }

    @Test
    void shouldSet24HourTTL() {
        redisService.recordTransaction(USER_ID, "TX001", 100.0);

        verify(redisTemplate).expire(KEY, Duration.ofHours(24));
    }


    @Test
    void shouldDetectAbnormalTransaction() {
        when(transactions.count(eq(KEY), anyDouble(), anyDouble())).thenReturn(3L);
        when(transactions.rangeByScore(eq(KEY), anyDouble(), anyDouble()))
                .thenReturn(java.util.Set.of(
                        "TX001:100.0",
                        "TX002:200.0",
                        "TX003:300.0"
                ));

        FdsGrpcServiceImpl grpcService = new FdsGrpcServiceImpl(redisService);
        FdsVerifyRequest request = FdsVerifyRequest.newBuilder()
                .setUserId(USER_ID)
                .setAccountId(1L)
                .setAmount(1_000_000.0)
                .build();

        AtomicReference<FdsVerifyResponse> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

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
                    }
                }
        );

        assertNull(error.get(), "FDS error: " + error.get());
        assertNotNull(result.get(), "FDS response is null");
        assertFalse(result.get().getAllowed(), "Abnormal transaction should be blocked");
        assertEquals("이번 거래를 포함하면 시간당 누적 금액 초과", result.get().getMessage());
    }




}
