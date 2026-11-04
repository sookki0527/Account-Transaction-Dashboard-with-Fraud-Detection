package org.example;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(
        named = "RUN_TRANSFER_TEST",
        matches = "true"
)
class TransferConcurrencyTest {

    @Test
    void twoAccountsTransferToSameAccount() throws Exception {
        String url = System.getenv("TRANSFER_URL");
        assertNotNull(url, "TRANSFER_URL을 설정하세요.");

        // 실제 존재하는 계좌와 해당 소유자 ID로 변경하세요.
        List<String> payloads = List.of(
                """
                {
                    "userId": "user1",
                    "fromAccountId": 1018,
                    "toAccountId": 1019,
                    "amount": 100
                }
                """,
                """
                {
                    "userId": "user2",
                    "fromAccountId": 1020,
                    "toAccountId": 1019,
                    "amount": 100
                }
                """
        );

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ExecutorService executor = Executors.newFixedThreadPool(2);

        // 두 스레드가 준비되었는지 확인하는 신호
        CountDownLatch ready = new CountDownLatch(2);

        // 두 스레드를 함께 출발시키는 신호
        CountDownLatch start = new CountDownLatch(1);

        List<Future<HttpResponse<String>>> futures = new ArrayList<>();

        try {
            for (String payload : payloads) {
                futures.add(executor.submit(() -> {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(20))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(payload))
                            .build();

                    ready.countDown();

                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("출발 신호 대기 초과");
                    }

                    return client.send(
                            request,
                            HttpResponse.BodyHandlers.ofString()
                    );
                }));
            }

            assertTrue(
                    ready.await(5, TimeUnit.SECONDS),
                    "두 스레드 준비 실패"
            );

            // 준비된 두 스레드를 함께 출발시킴
            start.countDown();

            List<HttpResponse<String>> responses = new ArrayList<>();

            for (Future<HttpResponse<String>> future : futures) {
                responses.add(future.get(30, TimeUnit.SECONDS));
            }

            for (HttpResponse<String> response : responses) {
                System.out.printf(
                        "status=%d, body=%s%n",
                        response.statusCode(),
                        response.body()
                );
            }

            assertAll(
                    responses.stream()
                            .map(response ->
                                    (org.junit.jupiter.api.function.Executable) () ->
                                            assertTrue(
                                                    response.statusCode() >= 200
                                                            && response.statusCode() < 300,
                                                    "송금 응답 오류: " + response.body()
                                            )
                            )
            );
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}