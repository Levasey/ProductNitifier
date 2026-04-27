package com.example.depositservice;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaConsumerConfigurationTest {

    @Test
    void consumerFactory_usesConfiguredKafkaConsumerProperties() {
        KafkaConsumerConfiguration configuration = configWithEnvironment();

        ConsumerFactory<String, Object> consumerFactory = configuration.consumerFactory();
        Map<String, Object> properties =
                ((DefaultKafkaConsumerFactory<String, Object>) consumerFactory).getConfigurationProperties();

        assertEquals("localhost:9092", properties.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals("deposit-group", properties.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals("read_committed", properties.get(ConsumerConfig.ISOLATION_LEVEL_CONFIG));
    }

    @Test
    void producerFactory_usesConfiguredBootstrapServers() {
        KafkaConsumerConfiguration configuration = configWithEnvironment();

        ProducerFactory<String, Object> producerFactory = configuration.producerFactory();
        Map<String, Object> properties =
                ((DefaultKafkaProducerFactory<String, Object>) producerFactory).getConfigurationProperties();

        assertEquals("localhost:9092", properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
    }

    @Test
    void helperBeans_areCreatedSuccessfully() {
        KafkaConsumerConfiguration configuration = configWithEnvironment();
        ConsumerFactory<String, Object> consumerFactory = configuration.consumerFactory();
        KafkaTemplate<String, Object> kafkaTemplate = configuration.kafkaTemplate(configuration.producerFactory());

        assertNotNull(kafkaTemplate);
        assertNotNull(configuration.kafkaListenerContainerFactory(consumerFactory, kafkaTemplate));
    }

    private KafkaConsumerConfiguration configWithEnvironment() {
        KafkaConsumerConfiguration configuration = new KafkaConsumerConfiguration();
        Environment environment = mock(Environment.class);
        when(environment.getProperty("spring.kafka.consumer.bootstrap-servers")).thenReturn("localhost:9092");
        when(environment.getProperty("spring.kafka.consumer.group-id")).thenReturn("deposit-group");
        when(environment.getProperty("spring.kafka.consumer.properties.spring.json.trusted.packages"))
                .thenReturn("com.example.core.events");
        when(environment.getProperty("spring.kafka.consumer.isolation-level", "READ_COMMITTED"))
                .thenReturn("READ_COMMITTED");

        ReflectionTestUtils.setField(configuration, "environment", environment);
        return configuration;
    }
}
