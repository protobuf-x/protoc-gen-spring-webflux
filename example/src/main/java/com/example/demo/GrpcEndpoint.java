package com.example.demo;

import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

@GRpcService
public class GrpcEndpoint extends EchoServiceGrpc.EchoServiceImplBase {

    @Override
    public void getEcho(GetEchoRequest request, StreamObserver<GetEchoResponse> responseObserver) {
        GetEchoResponse res = GetEchoResponse.newBuilder()
                .setEcho(Echo.newBuilder()
                        .setId(request.getId() * 10)
                        .setContent("text" + request.getId())
                        .build()
                )
                .build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();
    }

    @Override
    public void multiGetEcho(MultiGetEchoRequest request, StreamObserver<MultiGetEchoResponse> responseObserver) {
        super.multiGetEcho(request, responseObserver);
    }

    @Override
    public void multiGetEchoX(MultiGetEchoRequest request, StreamObserver<MultiGetEchoResponse> responseObserver) {
        super.multiGetEchoX(request, responseObserver);
    }

    @Override
    public void deleteEcho(DeleteEchoRequest request, StreamObserver<DeleteEchoResponse> responseObserver) {
        super.deleteEcho(request, responseObserver);
    }

    @Override
    public void newEcho(NewEchoRequest request, StreamObserver<NewEchoResponse> responseObserver) {
        super.newEcho(request, responseObserver);
    }

    @Override
    public void updateEcho(UpdateEchoRequest request, StreamObserver<UpdateEchoResponse> responseObserver) {
        super.updateEcho(request, responseObserver);
    }
}
