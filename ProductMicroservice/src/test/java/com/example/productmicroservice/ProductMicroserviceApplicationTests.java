package com.example.productmicroservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, count = 1, controlledShutdown = true)
class ProductMicroserviceApplicationTests {

    @Test
    void contextLoads() {
    }

}
