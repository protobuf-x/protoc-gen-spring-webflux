package com.example.demo;

import com.example.demo2.*;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

@GRpcService
public class BarService extends BarServiceGrpc.BarServiceImplBase {
    @Override
    public void getBar(GetBarRequest request, StreamObserver<Bar> responseObserver) {
        Bar res = Bar.newBuilder()
                .setId(request.getId())
                .setContent("BarService#getBar")
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();
    }
}
