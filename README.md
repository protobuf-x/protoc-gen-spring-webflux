# protoc-gen-spring-webflux
gRPC to JSON proxy generator protoc plugin for Spring WebFlux inspired by [grpc-gateway](https://github.com/grpc-ecosystem/grpc-gateway).

The protoc-gen-spring-webflux is a plugin of the Google protocol buffers compiler
[protoc](https://github.com/protocolbuffers/protobuf).
It reads protobuf service definitions and generates Spring WebFlux handler(for HTTP server) and proxy(for Proxy server) classes which
translates RPC and RESTful HTTP API into gRPC.

* WebFlux HTTP server vs WebFlux Proxy server
  * WebFlux HTTP server: By mapping the http request directly to the gRPC service call, 
  the gRPC server is converted to an Http server with minimal configuration.
  * WebFlux Proxy server: Uses an external HTTP server separately from the gRPC server to convert Http requests to gRPC calls.
  Designed for use cases like gateway patterns.
![image](https://user-images.githubusercontent.com/5003722/64909510-216e3a80-d748-11e9-9d50-c1fd789961b6.png)


* RPC style vs RESTful style
  * RPC style: The default for protoc-gen-spring-webflux is RPC style.
  It does not require [`google.api.http`](https://github.com/googleapis/googleapis/blob/master/google/api/http.proto#L46) and can be used with minimal definition.
  Ideal when you don't need a RESTful API.
  * RESTful style: RESTful API can be defined by using  [`google.api.http`](https://github.com/googleapis/googleapis/blob/master/google/api/http.proto#L46).
  It can also be used to change the default RPC style.

## Installation
* Download the latest binaries from [Release](https://github.com/protocol-buffers-extensions/protoc-gen-spring-webflux/releases).
* Since the protoc plugin is a jar file, it is recommended to use it from an [external script](./plugin/protoc-gen-spring-webflux).

## Usage

1. Define your [gRPC](https://grpc.io/docs/) service using protocol buffers.

```protoc:example.proto
syntax = "proto3";

package example.demo;

// messages...

service EchoService {

    rpc GetEcho(EchoRequest) returns (EchoResponse) {
    }

    rpc CreateEcho(CreateEchoRequest) returns (CreateEchoResponse) {
    }
}
```
2. (Optional) Add a [`google.api.http`](https://github.com/googleapis/googleapis/blob/master/google/api/http.proto#L46) annotation to your .proto file for REST style API.

```diff
syntax = "proto3";

package example.demo;
+import "google/api/annotations.proto";

// messages...

service EchoService {

    rpc GetEcho(EchoRequest) returns (EchoResponse) {
+        // If you use REST API style
+        option (google.api.http) = {
+            get: "/echo/{echo}"
+        };
    }

    rpc CreateEcho(CreateEchoRequest) returns (CreateEchoResponse) {
+        // If you use REST API style
+        option (google.api.http) = {
+              post: "/echo"
+              body: "*"
+        };
    }
}
```

3. Generate routing handler class using `protoc-gen-spring-webflux`

```bash
# When default configuration, provide RPC style API.
# In case of RPC style, API of grpc-web format(/{package}.{service}/{method}) is provided.
# ex. POST http://hostname/example.demo.EchoService/GetEcho
protoc -I. \
    --spring-webflux_out=. \
     example.proto

# If you use google.api.http and REST API style.
protoc -I. \
    -I$APIPATH/googleapis \
    --plugin=./protoc-gen-spring-webflux \
    --spring-webflux_out=style=rest:. \
     example.proto     
```

4-a. Write an routing of the Spring WebFlux `HTTP` server.

```java:HttpServerConfg.java
@Configuration
class HttpServerConfg {
    @Bean
    ExampleHandlers.EchoServiceHandler exampleHandlers(EchoService service) {
        // ExampleHandlers is a class generated by protoc-gen-spring-webflux
        return new ExampleHandlers.EchoServiceHandler(service);
    }

    @Bean
    RouterFunction<ServerResponse> routing(ExampleHandlers.EchoServiceHandler handler) {
        return RouterFunctions
            // Use the handleAll method to route everything to the generated Handler.
            .route(path("/echo*/**"), handler::handleAll)
            // Handler can be routed individually by using the generated method.
            .andRoute(GET("/echo/{id}"), handler::getEcho)
            .andRoute(POST("/echo"), handler::createEcho);
    }
}
```

4-b. Write an routing of the Spring WebFlux `Proxy` server.

```java:ProxyServerConfg.java
@Configuration
class ProxyServerConfg {
    @Bean
    ExampleHandlers.EchoServiceProxy exampleHandlers() {
        ManagedChannel channel = ManagedChannelBuilder.forAddress(/*...*/)
                .usePlaintext()
                .build();
        EchoServiceGrpc.EchoServiceStub stub = EchoServiceGrpc.newStub(channel);

        // ExampleHandlers is a class generated by protoc-gen-spring-webflux
        return new ExampleHandlers.EchoServiceProxy(stub);
    }

    @Bean
    RouterFunction<ServerResponse> routing(ExampleHandlers.EchoServiceProxy handler) {
        return RouterFunctions
            // Use the proxyAll method to route everything to the generated Proxy.
            .route(path("/echo*/**"), handler::proxyAll)
            // Handler can be routed individually by using the generated method.
            .andRoute(GET("/echo/{id}"), handler::getEcho)
            .andRoute(POST("/echo"), handler::createEcho);
    }
}
```

## Missing Features Shortlist
* Streams not supported.
* Custom patterns not supported.
* Variables not supported.
* Not supporting * and ** in path.
* Request header metadata mapping.


## License
(The MIT License)

Copyright (c) 2020 @disc99
