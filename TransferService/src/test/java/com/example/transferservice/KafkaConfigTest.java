package com.example.transferservice;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManagerFactory;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class KafkaConfigTest {

    @Test
    void producerConfigs_containsCriticalKafkaProducerSettings() {
        KafkaConfig config = configWithValues();

        Map<String, Object> producerConfigs = config.producerConfigs();

        assertEquals("localhost:9092", producerConfigs.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("all", producerConfigs.get(ProducerConfig.ACKS_CONFIG));
        assertEquals(true, producerConfigs.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        assertEquals(5, producerConfigs.get(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION));
    }

    @Test
    void producerFactory_setsTransactionPrefix() {
        KafkaConfig config = configWithValues();

        ProducerFactory<String, Object> producerFactory = config.producerFactory();

        assertNotNull(producerFactory);
        assertEquals("tx-", ((DefaultKafkaProducerFactory<String, Object>) producerFactory).getTransactionIdPrefix());
    }

    @Test
    void topicBeans_useConfiguredNames() {
        KafkaConfig config = configWithValues();

        assertEquals("withdraw-topic", config.createWithdrawTopic().name());
        assertEquals("deposit-topic", config.createDepositTopic().name());
    }

    @Test
    void kafkaBeans_areCreated() {
        KafkaConfig config = configWithValues();
        ProducerFactory<String, Object> producerFactory = config.producerFactory();

        KafkaTemplate<String, Object> kafkaTemplate = config.kafkaTemplate(producerFactory);
        KafkaTransactionManager<String, Object> transactionManager = config.kafkaTransactionManager(producerFactory);

        assertNotNull(kafkaTemplate);
        assertNotNull(transactionManager);
    }

    @Test
    void jpaTransactionManager_isCreatedForEntityManagerFactory() {
        KafkaConfig config = configWithValues();
        EntityManagerFactory entityManagerFactory = mock(EntityManagerFactory.class);

        JpaTransactionManager transactionManager = config.jpaTransactionManager(entityManagerFactory);

        assertNotNull(transactionManager);
        assertEquals(entityManagerFactory, transactionManager.getEntityManagerFactory());
    }

    private KafkaConfig configWithValues() {
        KafkaConfig config = new KafkaConfig();
        ReflectionTestUtils.setField(config, "withdrawTopicName", "withdraw-topic");
        ReflectionTestUtils.setField(config, "depositTopicName", "deposit-topic");
        ReflectionTestUtils.setField(config, "bootstrapServers", "localhost:9092");
        ReflectionTestUtils.setField(config, "keySerializer", "org.apache.kafka.common.serialization.StringSerializer");
        ReflectionTestUtils.setField(config, "valueSerializer", "org.springframework.kafka.support.serializer.JsonSerializer");
        ReflectionTestUtils.setField(config, "acks", "all");
        ReflectionTestUtils.setField(config, "deliveryTimeout", "120000");
        ReflectionTestUtils.setField(config, "linger", "0");
        ReflectionTestUtils.setField(config, "requestTimeout", "30000");
        ReflectionTestUtils.setField(config, "idempotence", true);
        ReflectionTestUtils.setField(config, "inflightRequests", 5);
        ReflectionTestUtils.setField(config, "transactionIdPrefix", "tx-");
        return config;
    }
}
