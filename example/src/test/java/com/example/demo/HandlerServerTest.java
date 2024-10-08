package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.BodyInserters.fromValue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "grpc.port=6567")
class HandlerServerTest {

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private WebTestClient client;

    @Test
    void test_get_path() {
        client.get().uri("/echo/1").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.id").isEqualTo("1")
                .jsonPath("$.echo.content").isEqualTo("EchoService#getEcho");
    }

    @Test
    void test_get_path2() {
        client.get().uri("/echo/contents/c").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.id").isEqualTo("2")
                .jsonPath("$.echo.content").isEqualTo("EchoService#getEchoByContent");
    }

    @Test
    void exception_get_path_mismatch_type() {
        client.get().uri("/echo/x").exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("For input string: \"x\"");

    }

    @Test
    void test_get_query_single() {
        client.get().uri("/echo_single?id=1").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.id").isEqualTo("1")
                .jsonPath("$.echo.content").isEqualTo("EchoService#singleGetEcho");
    }

    @Test
    void test_get_query_enum() {
        client.get().uri("/echo_enum?type=TYPE_B&types=TYPE_A&types=TYPE_B").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.id").isEqualTo("99")
                .jsonPath("$.echo.content").isEqualTo("EchoService#enumGetEcho:TYPE_B,[TYPE_A, TYPE_B]");
    }

    @Test
    void test_get_query() {
        client.get().uri("/echo?id=1").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo[0].id").isEqualTo("1")
                .jsonPath("$.echo[0].content").isEqualTo("EchoService#multiGetEcho");
    }

    @Test
    void test_get_query_array() {
        client.get().uri("/echo?id=1&id=2").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo[0].id").isEqualTo("1")
                .jsonPath("$.echo[0].content").isEqualTo("EchoService#multiGetEcho")
                .jsonPath("$.echo[1].id").isEqualTo("2")
                .jsonPath("$.echo[1].content").isEqualTo("EchoService#multiGetEcho");
    }

    @Test
    void test_get_query_blank() {
        client.get().uri("/echo?id=").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo").isArray()
                .jsonPath("$.echo").isEmpty();
    }

    @Test
    void test_get_query_empty() {
        client.get().uri("/echo").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo").isArray()
                .jsonPath("$.echo").isEmpty();
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
                .expectBody()
                .jsonPath("$.echo.id").isEqualTo("1")
                .jsonPath("$.echo.content").isEqualTo("EchoService#deleteEcho");
    }

    @Test
    void test_post_wildcard_body() {
        client.post()
                .uri("/echo").contentType(APPLICATION_JSON)
                .body(fromValue("{\"echo\":{\"id\":1,\"content\":\"test\"}}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.id").isEqualTo("1")
                .jsonPath("$.echo.content").isEqualTo("EchoService#newEcho:{id:0, content:, {id:1, content:test}}");
    }

    @Test
    void test_post_nested_path() {
        client.post()
                .uri("/echo/1").contentType(APPLICATION_JSON)
                .body(fromValue("{\"id\":10,\"content\":\"test\"}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.content").isEqualTo("EchoService#newEcho:{id:0, content:, {id:1, content:test}}");
    }

    @Test
    void test_post_wildcard_body_empty() {
        client.post()
                .uri("/echo").contentType(APPLICATION_JSON)
                .body(fromValue("{}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.id").isEqualTo("0")
                .jsonPath("$.echo.content").isEqualTo("EchoService#newEcho:{id:0, content:, {id:0, content:}}");
    }

    @Test
    void test_put() {
        client.put()
                .uri("/echo").contentType(APPLICATION_JSON)
                .body(fromValue("{\"id\":2,\"newEcho\":{\"id\":1,\"content\":\"test\"}}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.content").isEqualTo("EchoService#updateEcho:{id:0, {id:2, content:}}");
    }

    @Test
    void test_patch() {
        client.patch()
                .uri("/echo/1").contentType(APPLICATION_JSON)
                .body(fromValue("{\"id\":10,\"content\":\"test\"}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.content").isEqualTo("EchoService#updateEcho:{id:1, {id:10, content:test}}");
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
        client.get()
                .uri("/echoHeader")
                .header("my-header-1", "value-1")
                .header("my-header-2", "value-2")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.content").isEqualTo("value-2");
    }

    @Test
    void empty() {
        client.post()
                .uri("/echoEmpty").contentType(APPLICATION_JSON)
                .body(fromValue("{}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("{}");
    }

    @Test
    void types() {
        client.get().uri("/echoTypes?mask=f1,f2,f3&time=2018-01-15T01:30:15.01Z&range=3s").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.json", "mask {\n  paths: \"f1\"\n  paths: \"f2\"\n  paths: \"f3\"\n}\ntime {\n  seconds: 1515979815\n  nanos: 10000000\n}\nrange {\n  seconds: 3\n}\n");
    }

    @Test
    void customMethod() {
        client.post()
                .uri("/echo:custom").contentType(APPLICATION_JSON)
                .body(fromValue("{\"echo\":{\"id\":1,\"content\":\"test\"}}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.echo.id").isEqualTo("1")
                .jsonPath("$.echo.content").isEqualTo("EchoService#customEcho:{id:1, content:test}");
    }


    @Test
    void customMethod2() {
        client.post()
                .uri("/echo/tom:custom").contentType(APPLICATION_JSON)
                .body(fromValue("{\"age\":10}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.json").isEqualTo("name: \"tom\"\nage: 10\n");
    }

    @Test
    void customMethod3() {
        client.post()
                .uri("/echo/tom1/child/tom2:custom").contentType(APPLICATION_JSON)
                .body(fromValue("{\"age\":10}")).exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.json").isEqualTo("name1: \"tom1\"\nname2: \"tom2\"\nage: 10\n");
    }
}
