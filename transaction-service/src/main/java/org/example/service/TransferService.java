package org.example.service;

import org.example.dto.Account;
import org.example.dto.DepositRequest;

import net.devh.boot.grpc.client.inject.GrpcClient;
import org.example.grpc.fds.FdsCheckServiceGrpc;
import org.example.grpc.fds.FdsVerifyRequest;
import org.example.grpc.fds.FdsVerifyResponse;

import org.example.dto.TransactionDto;
import org.example.dto.TransferRequest;
import org.example.entity.Transaction;
import org.example.repository.TransferRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;
import java.util.List;

@Service
public class TransferService {

    private final RestTemplate restTemplate;
    private final String ACCOUNT_SERVICE_BASE_URL = "http://account-service:8081/accounts/account";
    private final KafkaProducer kafkaProducer;
    private final TransferRepository transferRepository;
    private final RedissonClient redissonClient;

    // application.yml에 설정된 fds-service 주소와 매핑됨
    @GrpcClient("fds-service")
    private FdsCheckServiceGrpc.FdsCheckServiceBlockingStub fdsClient;


    @Autowired
    public TransferService(RestTemplate restTemplate, KafkaProducer kafkaProducer, TransferRepository transferRepository,
        RedissonClient redissonClient) {
        this.restTemplate = restTemplate;
        this.kafkaProducer = kafkaProducer;
        this.transferRepository = transferRepository;
        this.redissonClient = redissonClient;
    }

    public void transfer(TransferRequest request) {


        FdsVerifyRequest fdsRequest = FdsVerifyRequest.newBuilder()
                .setUserId(request.getUserId())
                .setFromAccountId(request.getFromAccountId())
                .setAmount(request.getAmount().doubleValue())
                .build();

        try {
            // gRPC 호출 (밀리초 단위 고속 통신)
            FdsVerifyResponse fdsResponse = fdsClient.verifyTransaction(fdsRequest);

            if (!fdsResponse.getAllowed()) {
                throw new IllegalStateException("Blocked by FDS: " + fdsResponse.getMessage());
            }
        } catch (io.grpc.StatusRuntimeException e) {
            // FDS 서비스 장애 시 대책 (서킷 브레이커 또는 Fail-Open/Fail-Close 정책 적용)
            throw new RuntimeException("FDS Service is unavailable", e);
        }


        // Deadlock 방지를 위해 계좌 ID를 정렬하여 락 순서 고정
        String firstLockKey = "lock:account:" + Math.min(request.getFromAccountId(), request.getToAccountId());
        String secondLockKey = "lock:account:" + Math.max(request.getFromAccountId(), request.getToAccountId());

        RLock lock1 = redissonClient.getLock(firstLockKey);
        RLock lock2 = redissonClient.getLock(secondLockKey);

        try {
            // 락 획득 대기 시간(Wait Time) 3초, 락 자동 해제 시간(Lease Time) 5초
            boolean isLocked = lock1.tryLock(3, 5, TimeUnit.SECONDS);
            if (!isLocked) {
                throw new IllegalStateException("Could not acquire lock for first account");
            }

            boolean isLocked2 = lock2.tryLock(3, 5, TimeUnit.SECONDS);
            if (!isLocked2) {
                lock1.unlock(); 
                throw new IllegalStateException("Could not acquire lock for second account");
            }

            try {
                Account from = restTemplate.getForObject(
                        ACCOUNT_SERVICE_BASE_URL + "/" + request.getFromAccountId(), Account.class
                );
                Account to = restTemplate.getForObject(
                        ACCOUNT_SERVICE_BASE_URL + "/" + request.getToAccountId(), Account.class
                );

            
                if(from.getBalance().compareTo(request.getAmount()) < 0) {
                    throw new IllegalArgumentException("Insufficient Balance");
                }

               
                from.setBalance(from.getBalance().subtract(request.getAmount()));
                to.setBalance(to.getBalance().add(request.getAmount()));

            
                restTemplate.put(ACCOUNT_SERVICE_BASE_URL + "/" + request.getFromAccountId() + "/withdraw", from);
                restTemplate.put(ACCOUNT_SERVICE_BASE_URL + "/" + request.getToAccountId() + "/deposit", to);

                Transaction transaction = Transaction.builder()
                        .id(request.getTransactionId())
                        .userId(request.getUserId())
                        .fromAccountId(request.getFromAccountId())
                        .toAccountId(request.getToAccountId())
                        .amount(request.getAmount())
                        .date(request.getDate())
                        .type(request.getType())
                        .build();
                
                transferRepository.save(transaction);
                kafkaProducer.sendTransferNotification(request);

                System.out.println("kafka produced " + request);
                System.out.println("transaction saved " + transaction);

                

            } finally {
                
                if (lock2.isHeldByCurrentThread()) {
                    lock2.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while waiting for lock", e);
        } finally {
            
            if (lock1.isHeldByCurrentThread()) {
                lock1.unlock();
            }
        }
    }
    
    public void deposit(DepositRequest request) {

        FdsVerifyRequest fdsRequest = FdsVerifyRequest.newBuilder()
                .setUserId(request.getUserId())
                .setAccountId(request.getAccountId())
                .setAmount(request.getAmount().doubleValue())
                .build();

        try {
            // gRPC 호출 (밀리초 단위 고속 통신)
            FdsVerifyResponse fdsResponse = fdsClient.verifyTransaction(fdsRequest);

            if (!fdsResponse.getAllowed()) {
                throw new IllegalStateException("Blocked by FDS: " + fdsResponse.getMessage());
            }
        } catch (io.grpc.StatusRuntimeException e) {
            // FDS 서비스 장애 시 대책 (서킷 브레이커 또는 Fail-Open/Fail-Close 정책 적용)
            throw new RuntimeException("FDS Service is unavailable", e);
        }


        Account account = restTemplate.getForObject(
                ACCOUNT_SERVICE_BASE_URL + "/" + request.getAccountId(), Account.class
        );

        account.setBalance(account.getBalance().add(request.getAmount()));

        restTemplate.put(ACCOUNT_SERVICE_BASE_URL + "/" + request.getAccountId() + "/deposit", account);

        Transaction transaction = Transaction.builder()
                .userId(request.getUserId())
                .toAccountId(request.getAccountId())
                .amount(request.getAmount())
                .date(request.getDate())
                .type("DEPOSIT")
                .build();

        transferRepository.save(transaction);

    } 
    

    public void withdraw(DepositRequest request) {

        FdsVerifyRequest fdsRequest = FdsVerifyRequest.newBuilder()
                .setUserId(request.getUserId())
                .setAccountId(request.getAccountId())
                .setAmount(request.getAmount().doubleValue())
                .build();

        try {
            // gRPC 호출 (밀리초 단위 고속 통신)
            FdsVerifyResponse fdsResponse = fdsClient.verifyTransaction(fdsRequest);

            if (!fdsResponse.getAllowed()) {
                throw new IllegalStateException("Blocked by FDS: " + fdsResponse.getMessage());
            }
        } catch (io.grpc.StatusRuntimeException e) {
            // FDS 서비스 장애 시 대책 (서킷 브레이커 또는 Fail-Open/Fail-Close 정책 적용)
            throw new RuntimeException("FDS Service is unavailable", e);
        }

        Account account = restTemplate.getForObject(
                ACCOUNT_SERVICE_BASE_URL + "/" + request.getAccountId(), Account.class
        );

        if(account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new IllegalArgumentException("Insufficient Balance");
        }

        account.setBalance(account.getBalance().subtract(request.getAmount()));
        restTemplate.put(ACCOUNT_SERVICE_BASE_URL + "/" + request.getAccountId() + "/withdraw", account);
    }   


    

    public List<TransactionDto> getAllTransactions(String userId){
        List<Transaction> transactions = transferRepository.findTransactionsByUserId(userId);
        return transactions.stream().map(transaction -> new TransactionDto(transaction.getId(), transaction.getUserId(),
                transaction.getFromAccountId(), transaction.getToAccountId(), transaction.getAmount(), transaction.getType(),
                transaction.getDate())).toList();
    }


}
