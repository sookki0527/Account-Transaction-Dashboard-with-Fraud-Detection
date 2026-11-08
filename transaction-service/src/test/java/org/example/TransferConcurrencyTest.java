
package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.*;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class TransferConcurrencyTest {

    private static final String TRANSACTION_URL =
        "http://localhost:8082";

    private static final String ACCOUNT_URL =
            "http://localhost:8081";

    private static final String USER_URL =
            "http://localhost:8084";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    // 테스트 전 계좌와 사용자 3명 생성 필요
    private final long accountA = 1;
    private final long accountB = 2;
    private final long accountC = 3;
    
    @BeforeEach
    void resetBalances() throws Exception {
        for (long accountId = 1; accountId <= 11; accountId++) {
            resetBalance(accountId, 100000);
        }
    }

    @Test
    void twoUsersTransferToSameAccount() throws Exception {

        // 1. 사용자 3명 로그인
        String tokenA = login("user1", "123456");
        String tokenB = login("user2", "123456");
        String tokenC = login("user3", "123456");

        // 2. 초기 잔액 확인
        assertBalance(accountA, tokenA, "100000");
        assertBalance(accountB, tokenB, "100000");
        assertBalance(accountC, tokenC, "100000");

        ExecutorService executor = Executors.newFixedThreadPool(2);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
            // 3. 사용자 A의 송금
            Future<Integer> futureA = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Start timeout");
                }
                return transfer(tokenA, "user1", accountA, accountC, 10000);
            });

            // 4. 사용자 B의 송금
            Future<Integer> futureB = executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new TimeoutException("Start timeout");
                }
                return transfer(tokenB, "user2", accountB, accountC, 10000);
            });

            // 두 스레드가 모두 준비될 때까지 기다림
            assertTrue(ready.await(10, TimeUnit.SECONDS));

            // 5. 동시에 송금 시작
            start.countDown();

            int resultA = futureA.get(30, TimeUnit.SECONDS);
            int resultB = futureB.get(30, TimeUnit.SECONDS);

            
            System.out.println("Transfer A HTTP Status: " + resultA);
            System.out.println("Transfer B HTTP Status: " + resultB);

            assertTrue(
                resultA >= 200 && resultA < 300,
                "Transfer A failed. HTTP status: " + resultA
            );

            assertTrue(
                resultB >= 200 && resultB < 300,
                "Transfer B failed. HTTP status: " + resultB
            );

            // 6. 최종 잔액 확인
            assertBalance(accountA, tokenA, "90000");
            assertBalance(accountB, tokenB, "90000");
            assertBalance(accountC, tokenC, "120000");

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }


    
    @Test
    void tenUsersTransferToSameAccount() throws Exception {

        final int senderCount = 10;
        final int amount = 10000;

        // 실제 DB에 존재하는 수취 계좌 ID로 변경
        final long receiverAccountId = 11L;

        // user1 ~ user10의 계좌 ID라고 가정
        long[] senderAccountIds = {
            1L, 2L, 3L, 4L, 5L,
            6L, 7L, 8L, 9L, 10L
        };

        String[] senderTokens = new String[senderCount];

        // 1. 송금자 10명 로그인 및 초기 잔액 검사
        for (int i = 0; i < senderCount; i++) {

            String userId = "user" + (i + 1);

            senderTokens[i] = login(userId, "123456");

            assertBalance(
                senderAccountIds[i],
                senderTokens[i],
                "100000"
            );
        }

        // 2. 수취인 로그인
        String receiverToken = login("user11", "123456");

        assertBalance(receiverAccountId, receiverToken, "100000");

        ExecutorService executor =
                Executors.newFixedThreadPool(senderCount);

        CountDownLatch ready = new CountDownLatch(senderCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Integer>> futures = new ArrayList<>();

            // 3. 송금 요청 10개 준비
            for (int i = 0; i < senderCount; i++) {

                final int index = i;

                futures.add(executor.submit(() -> {

                    ready.countDown();

                    if (!start.await(15, TimeUnit.SECONDS)) {
                        throw new TimeoutException("Start timeout");
                    }

                    String userId = "user" + (index + 1);

                    return transfer(
                        senderTokens[index],
                        userId,
                        senderAccountIds[index],
                        receiverAccountId,
                        amount
                    );
                }));
            }

            // 4. 스레드 10개 준비 완료
            assertTrue(
                ready.await(15, TimeUnit.SECONDS),
                "Not all threads are ready"
            );

            // 5. 동시 송금 시작
            start.countDown();

            int successCount = 0;

            // 6. HTTP 응답 확인
            for (int i = 0; i < senderCount; i++) {

                int status = futures.get(i)
                        .get(60, TimeUnit.SECONDS);

                System.out.println(
                    "user" + (i + 1)
                    + " transfer HTTP status: " + status
                );

                if (status >= 200 && status < 300) {
                    successCount++;
                }
            }

            assertEquals(
                senderCount,
                successCount,
                "Some transfers failed"
            );

            // 7. 송금자 잔액 검증
            for (int i = 0; i < senderCount; i++) {

                assertBalance(
                    senderAccountIds[i],
                    senderTokens[i],
                    "90000"
                );
            }

            // 8. 수취인 최종 잔액 검증
            assertBalance(
                receiverAccountId,
                receiverToken,
                "200000"
            );

        } finally {
            start.countDown();
            executor.shutdownNow();
        }
}




    private String login(String username, String password)
            throws Exception {

        String json = mapper.writeValueAsString(
                java.util.Map.of(
                        "username", username,
                        "password", password
                )
        );

        HttpResponse<String> response =
                post(USER_URL + "/auth/login", json, null);

        assertEquals(200, response.statusCode(),
                "Login failed for " + username + ": " + response.body());

        return mapper.readTree(response.body())
                .get("token")
                .asText();
    }

    private int transfer(
            String token,
            String userId,
            long fromAccountId,
            long toAccountId,
            int amount
    ) throws Exception {

        String json = mapper.writeValueAsString(
                java.util.Map.of(
                        "userId", userId,
                        "fromAccountId", fromAccountId,
                        "toAccountId", toAccountId,
                        "amount", amount,
                        "date", Instant.now().toString()
                )
        );

        return post(TRANSACTION_URL + "/transactions/transfer", json, token)
                .statusCode();
    }

    private HttpResponse<String> post(
            String path, String json, String token
    ) throws Exception {

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(path))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return client.send(
                builder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
    }

    private void assertBalance(
            long accountId, String token, String expected
    ) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ACCOUNT_URL + "/accounts/account/" + accountId))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode());

        JsonNode account = mapper.readTree(response.body());

        BigDecimal actual = new BigDecimal(
                account.get("balance").asText()
        );

         System.out.println(
            "Account ID: " + accountId
            + " | Expected: " + expected
            + " | Actual: " + actual
    );

        assertEquals(
                0,
                new BigDecimal(expected).compareTo(actual)
        );
    }


    private void resetBalance(long accountId, int balance)
        throws Exception {
        String accountUrl = ACCOUNT_URL + "/accounts/account/" + accountId;
        HttpRequest getRequest = HttpRequest.newBuilder()
                .uri(URI.create(accountUrl))
                .GET()
                .build();
        HttpResponse<String> getResponse = client.send(
                getRequest,
                HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(200, getResponse.statusCode(),
                "Could not read balance for account " + accountId);

        BigDecimal currentBalance = new BigDecimal(
                mapper.readTree(getResponse.body()).get("balance").asText()
        );
        BigDecimal difference = BigDecimal.valueOf(balance)
                .subtract(currentBalance);
        if (difference.signum() == 0) {
            return;
        }

        String json = mapper.writeValueAsString(
                java.util.Map.of("amount", difference.abs())
        );
        String operation = difference.signum() > 0 ? "deposit" : "withdraw";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(accountUrl + "/" + operation))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        assertEquals(200, response.statusCode(),
                "Could not reset balance for account " + accountId);
    }
}
