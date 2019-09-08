package com.example.demo;

import com.example.demo.ExampleHandlers.EchoServiceHandler;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
    EchoServiceHandler exampleHandlers() {
		ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 6565)
				.usePlaintext()
				.build();
		EchoServiceGrpc.EchoServiceBlockingStub stub = EchoServiceGrpc.newBlockingStub(channel);
		return new EchoServiceHandler(stub);
	}

	@Bean
	RouterFunction<ServerResponse> routing(EchoServiceHandler handler) {
		return RouterFunctions
				.route(path("/echo*/**"), handler::handleAll)
				.andRoute(path("/example.demo.EchoService/**"), handler::handleAll)
				;
	}
}
