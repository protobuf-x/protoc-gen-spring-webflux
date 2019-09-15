package com.example.demo;

import com.example.demo2.Foo;
import com.example.demo2.FooServiceGrpc;
import com.example.demo2.GetFooRequest;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;

@GRpcService
public class FooService extends FooServiceGrpc.FooServiceImplBase {
    @Override
    public void getFoo(GetFooRequest request, StreamObserver<Foo> responseObserver) {
        Foo res = Foo.newBuilder()
                .setId(request.getId())
                .setContent("FooService#getFoo")
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();
    }
}
