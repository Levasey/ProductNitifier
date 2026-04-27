package com.example.productmicroservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KafkaConfigTest {

    @Test
    void producerConfigs_containsCriticalKafkaProducerSettings() {
        KafkaConfig config = configWithValues();

        Map<String, Object> producerConfigs = config.producerConfigs();

        assertEquals("localhost:9092", producerConfigs.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("all", producerConfigs.get(ProducerConfig.ACKS_CONFIG));
        assertEquals("true", producerConfigs.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
    }

    @Test
    void topicBean_hasExpectedDurabilityConfig() {
        KafkaConfig config = configWithValues();

        assertEquals("product-created-events-topic", config.createTopic().name());
        assertNotNull(config.createTopic().configs());
        assertEquals("2", config.createTopic().configs().get("min.insync.replicas"));
    }

    private KafkaConfig configWithValues() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "keySerializer", "org.apache.kafka.common.serialization.StringSerializer");
        ReflectionTestUtils.setField(config, "valueSerializer", "org.springframework.kafka.support.serializer.JsonSerializer");
        ReflectionTestUtils.setField(config, "acks", "all");
        ReflectionTestUtils.setField(config, "deliveryTimeoutMs", "120000");
        ReflectionTestUtils.setField(config, "linger", "0");
        ReflectionTestUtils.setField(config, "requestTimeout", "30000");
        ReflectionTestUtils.setField(config, "idempotence", "true");
        ReflectionTestUtils.setField(config, "maxInFlightRequests", "5");
        return config;
    }
}
