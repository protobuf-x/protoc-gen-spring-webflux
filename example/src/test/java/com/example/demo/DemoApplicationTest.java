package com.example.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DemoApplicationTest {

    @Autowired
    private WebTestClient client;

    @Test
    void test() {
        client.get().uri("/echo/1").exchange()
                .expectBody()
                .json("{\"echo\":{\"id\":10,\"content\":\"text1\"}}");
    }
}
