package com.cobre.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "cobre.notification.webhook.allow-insecure-localhost=true",
        "cobre.notification.webhook.max-attempts=5",
        "cobre.notification.webhook.initial-backoff-ms=5"
})
@AutoConfigureMockMvc
class CobreNotificationApplicationTests {

    @Test
    void contextLoads() {
    }
}

