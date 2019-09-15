package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.BodyInserters.fromObject;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "grpc.port=6567")
@ActiveProfiles("proxy-server")
public class ProxyServerTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private WebTestClient client;

    @Test
    void test_get_path() {
        client.get().uri("/echo/1").exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("{\"echo\":{\"id\":1,\"content\":\"EchoService#getEcho\"}}");
    }

    @Test
    void exception_get_path_mismatch_type() {
        client.get().uri("/echo/x").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("For input string: \"x\"");

    }

    @Test
    void test_get_query() {
        client.get().uri("/echo?id=1").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":[{\"id\":1,\"content\":\"EchoService#multiGetEcho\"}]}");
    }

    @Test
    void test_get_query_array() {
        client.get().uri("/echo?id=1&id=2").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":[{\"id\":1,\"content\":\"EchoService#multiGetEcho\"},{\"id\":2,\"content\":\"EchoService#multiGetEcho\"}]}");
    }

    @Test
    void test_get_query_blank() {
        client.get().uri("/echo?id=").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":[]}");
    }

    @Test
    void test_get_query_empty() {
        client.get().uri("/echo").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":[]}");
    }

    @Test
    void exception_get_query_mismatch_type() {
        client.get().uri("/echo?id=x").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("For input string: \"x\"");
    }

    @Test
    void test_delete() {
        client.delete()
                .uri("/echo/1").exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":{\"id\":1,\"content\":\"EchoService#deleteEcho\"}}");
    }

    @Test
    void test_post_wildcard_body() {
        client.post()
                .uri("/echo").contentType(APPLICATION_JSON)
                .body(fromObject("{\"echo\":{\"id\":1,\"content\":\"test\"}}")).exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":{\"id\":1,\"content\":\"EchoService#newEcho\"}}");
    }

    @Test
    void test_post_wildcard_body_empty() {
        client.post()
                .uri("/echo").contentType(APPLICATION_JSON)
                .body(fromObject("{}")).exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":{\"id\":0,\"content\":\"EchoService#newEcho\"}}");
    }

    @Test
    void test_post_specify_body() {
        client.post()
                .uri("/echo/in").contentType(APPLICATION_JSON)
                .body(fromObject("{\"id\":1,\"content\":\"test\"}")).exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":{\"id\":1,\"content\":\"EchoService#newEcho\"}}");
    }

    @Test
    void test_post_nested_path() {
        client.post()
                .uri("/echo/1").contentType(APPLICATION_JSON)
                .body(fromObject("{\"id\":100,\"content\":\"test\"}")).exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":{\"id\":1,\"content\":\"EchoService#newEcho\"}}");
    }

    @Test
    void test_put() {
        client.put()
                .uri("/echo").contentType(APPLICATION_JSON)
                .body(fromObject("{\"id\":1,\"content\":\"test\"}")).exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":{\"id\":1,\"content\":\"EchoService#updateEcho\"}}");
    }

    @Test
    void test_patch() {
        client.patch()
                .uri("/echo/1").contentType(APPLICATION_JSON)
                .body(fromObject("{\"id\":100,\"content\":\"test\"}")).exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"echo\":{\"id\":100,\"content\":\"EchoService#updateEcho\"}}");
    }

    @Test
    void exception_grpc_server_handle() {
        client.get()
                .uri("/echo/error/1").exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.message", "INVALID_ARGUMENT: Handled Exception!");

    }

    @Test
    void exception_grpc_server_no_handle() {
        client.get()
                .uri("/echo/error/2").exchange()
                .expectStatus().is5xxServerError()
                .expectBody().jsonPath("$.message", "UNKNOWN");
    }

    @Test
    void test_header() {
        // TODO
    }

    @Test
    void test_post_rpc_with_multiple_service_definition() {
        client.post()
                .uri("/example.demo2.FooService/GetFoo").contentType(APPLICATION_JSON)
                .body(fromObject("{\"id\":1}")).exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"id\":1,\"content\":\"FooService#getFoo\"}");
        client.post()
                .uri("/example.demo2.BarService/GetBar").contentType(APPLICATION_JSON)
                .body(fromObject("{\"id\":1}")).exchange()
                .expectStatus().isOk()
                .expectBody().json("{\"id\":1,\"content\":\"BarService#getBar\"}");
    }
}
