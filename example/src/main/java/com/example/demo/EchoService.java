package com.example.demo;

import com.example.demo.DemoApplication.HeaderInterceptor;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Durations;
import com.google.protobuf.util.JsonFormat;
import com.google.rpc.Code;
import com.google.rpc.Status;
import io.grpc.protobuf.StatusProto;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

import static java.util.stream.Collectors.toList;

@GRpcService(interceptors = {HeaderInterceptor.class})
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
    public void enumGetEcho(EnumGetEchoRequest request, StreamObserver<EnumGetEchoResponse> responseObserver) {
        EnumGetEchoResponse res = EnumGetEchoResponse.newBuilder()
                .setEcho(createEcho(99, "EchoService#enumGetEcho:" + request.getType() + "," + request.getTypesList()))
                .build();

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
                .setEcho(createEcho(request.getEcho().getId(),
                        String.format("EchoService#newEcho:{id:%s, content:%s, {id:%s, content:%s}}",
                                request.getId(), request.getContent(), request.getEcho().getId(), request.getEcho().getContent())))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void updateEcho(UpdateEchoRequest request, StreamObserver<UpdateEchoResponse> responseObserver) {
        UpdateEchoResponse res = UpdateEchoResponse.newBuilder()
                .setEcho(createEcho(request.getNewEcho().getId(),
                        String.format("EchoService#updateEcho:{id:%s, {id:%s, content:%s}}",
                                request.getId(), request.getNewEcho().getId(), request.getNewEcho().getContent())))
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

    @Override
    public void getEchoHeader(GetEchoRequest request, StreamObserver<GetEchoResponse> responseObserver) {
        GetEchoResponse res = GetEchoResponse.newBuilder()
                .setEcho(createEcho(request.getId(), HeaderInterceptor.HEADER_2.get()))
                .build();

        ok(responseObserver, res);
    }

    @Override
    public void emptyEcho(EmptyRequest request, StreamObserver<EmptyResponse> responseObserver) {
        EmptyResponse res = EmptyResponse.getDefaultInstance();
        ok(responseObserver, res);
    }

    @Override
    public void typesEcho(TypesRequest request, StreamObserver<TypesResponse> responseObserver) {
        TypesResponse res = TypesResponse.newBuilder()
                .setJson(request.toString())
                .build();
        ok(responseObserver, res);
    }

    @Override
    public void customEcho(CustomRequest request, StreamObserver<CustomResponse> responseObserver) {
        CustomResponse res = CustomResponse.newBuilder()
                .setEcho(createEcho(request.getEcho().getId(),
                        String.format("EchoService#customEcho:{id:%s, content:%s}", request.getEcho().getId(), request.getEcho().getContent())))
                .build();

        ok(responseObserver, res);
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
