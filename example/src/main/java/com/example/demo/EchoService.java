package com.example.demo;

import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

import static java.util.stream.Collectors.toList;

@GRpcService
public class EchoService extends EchoServiceGrpc.EchoServiceImplBase {

    @Override
    public void getEcho(GetEchoRequest request, StreamObserver<GetEchoResponse> responseObserver) {
        GetEchoResponse res = GetEchoResponse.newBuilder()
                .setEcho(createEcho(request.getId(), "GrpcEndpoint#getEcho"))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void multiGetEcho(MultiGetEchoRequest request, StreamObserver<MultiGetEchoResponse> responseObserver) {
        MultiGetEchoResponse res = MultiGetEchoResponse.newBuilder()
                .addAllEcho(request.getIdList().stream()
                        .map(id -> createEcho(id, "GrpcEndpoint#multiGetEcho"))
                        .collect(toList())
                ).build();

        ok(responseObserver, res);
    }

    @Override
    public void deleteEcho(DeleteEchoRequest request, StreamObserver<DeleteEchoResponse> responseObserver) {
        DeleteEchoResponse res = DeleteEchoResponse.newBuilder()
                .setEcho(createEcho(request.getId(), "GrpcEndpoint#deleteEcho"))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void newEcho(NewEchoRequest request, StreamObserver<NewEchoResponse> responseObserver) {
        NewEchoResponse res = NewEchoResponse.newBuilder()
                .setEcho(createEcho(request.getEcho().getId(), "GrpcEndpoint#newEcho"))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void updateEcho(UpdateEchoRequest request, StreamObserver<UpdateEchoResponse> responseObserver) {
        UpdateEchoResponse res = UpdateEchoResponse.newBuilder()
                .setEcho(createEcho(request.getNewEcho().getId(), "GrpcEndpoint#updateEcho"))
                .build();
        ok(responseObserver, res);
    }

    @Override
    public void errorEcho(ErrorEchoRequest request, StreamObserver<ErrorEchoResponse> responseObserver) {
        if (request.getId() == 1) {
            Status status = Status.newBuilder()
                    .setCode(Code.INVALID_ARGUMENT_VALUE)
                    .setMessage("Handled Exception!")
                    .build();
            responseObserver.onError(StatusProto.toStatusRuntimeException(status));
        }
        throw new IllegalStateException("No handled Exception!");
    }

    private Echo createEcho(long id, String text) {
        return Echo.newBuilder()
                .setId(id)
                .setContent(text)
                .build();
    }

    private <T> void ok(StreamObserver<T> responseObserver, T res) {
        responseObserver.onNext(res);
        responseObserver.onCompleted();
    }
}
