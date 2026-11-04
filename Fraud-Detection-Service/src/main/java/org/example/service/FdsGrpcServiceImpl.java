package org.example.service;

import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.example.grpc.fds.FdsCheckServiceGrpc;
import org.example.grpc.fds.FdsVerifyRequest;
import org.example.grpc.fds.FdsVerifyResponse;


@GrpcService // 스프링 부트에서 gRPC 서버 컴포넌트로 자동 등록
public class FdsGrpcServiceImpl extends FdsCheckServiceGrpc.FdsCheckServiceImplBase {

    private final RedisTimeSeriesService timeSeriesService;

    public FdsGrpcServiceImpl(RedisTimeSeriesService timeSeriesService) {
        this.timeSeriesService = timeSeriesService;
    }

    @Override
    public void verifyTransaction(FdsVerifyRequest request, StreamObserver<FdsVerifyResponse> responseObserver) {
        // 1. Redis에서 최근 1시간 송금 횟수 검증 수행
        var stats = timeSeriesService.getRecentWindowStats(request.getUserId(), 60 * 60 * 1000L);
    
        boolean isAllowed = true;
        String reason = "PASS";

        double hourlyAmountLimit = 1_000_000; 

        if (stats.count() >= 5) {
            isAllowed = false;
            reason = "최근 1시간 거래 횟수 초과";
        } else if (stats.totalAmount() + request.getAmount()
                > hourlyAmountLimit) {
            isAllowed = false;
            reason = "이번 거래를 포함하면 시간당 누적 금액 초과";
        }


        // 2. 결과 응답 객체 빌드
        FdsVerifyResponse response = FdsVerifyResponse.newBuilder()
                .setAllowed(isAllowed)
                .setMessage(reason)
                .build();

        // 3. 클라이언트로 응답 전송
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}