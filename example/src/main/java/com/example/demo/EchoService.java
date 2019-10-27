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
                .setEcho(createEcho(request.getId(), "EchoService#getEcho"))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void getEchoByContent(GetEchoByContentRequest request, StreamObserver<GetEchoResponse> responseObserver) {
        GetEchoResponse res = GetEchoResponse.newBuilder()
                .setEcho(createEcho(2, "EchoService#getEchoByContent"))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void singleGetEcho(SingleGetEchoRequest request, StreamObserver<SingleGetEchoResponse> responseObserver) {
        SingleGetEchoResponse res = SingleGetEchoResponse.newBuilder()
                .setEcho(createEcho(Long.parseLong(request.getId()), "EchoService#singleGetEcho"))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void multiGetEcho(MultiGetEchoRequest request, StreamObserver<MultiGetEchoResponse> responseObserver) {
        MultiGetEchoResponse res = MultiGetEchoResponse.newBuilder()
                .addAllEcho(request.getIdList().stream()
                        .map(id -> createEcho(id, "EchoService#multiGetEcho"))
                        .collect(toList())
                ).build();

        ok(responseObserver, res);
    }

    @Override
    public void deleteEcho(DeleteEchoRequest request, StreamObserver<DeleteEchoResponse> responseObserver) {
        DeleteEchoResponse res = DeleteEchoResponse.newBuilder()
                .setEcho(createEcho(request.getId(), "EchoService#deleteEcho"))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void newEcho(NewEchoRequest request, StreamObserver<NewEchoResponse> responseObserver) {
        NewEchoResponse res = NewEchoResponse.newBuilder()
                .setEcho(createEcho(request.getEcho().getId(), "EchoService#newEcho"))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void updateEcho(UpdateEchoRequest request, StreamObserver<UpdateEchoResponse> responseObserver) {
        UpdateEchoResponse res = UpdateEchoResponse.newBuilder()
                .setEcho(createEcho(request.getNewEcho().getId(), "EchoService#updateEcho"))
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
