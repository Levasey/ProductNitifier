package com.example.productmicroservice;

import com.example.core.events.ProductCreatedEvent;
import com.example.productmicroservice.service.ProductService;
import com.example.productmicroservice.service.dto.CreateProductDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DirtiesContext
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 3, count = 1, controlledShutdown = true)
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
public class ProductServiceIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private Environment environment;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    private KafkaMessageListenerContainer<String, ProductCreatedEvent> container;

    private BlockingQueue<ConsumerRecord<String, ProductCreatedEvent>> records;

    @BeforeAll
    void setUp() {
        DefaultKafkaConsumerFactory<String, Object> consumerFactory = new DefaultKafkaConsumerFactory<>(getConsumerProperties());
        ContainerProperties containerProperties = new ContainerProperties(environment.getProperty("product-created-events-topic-name"));

        container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        records = new LinkedBlockingQueue<>();
        container.setupMessageListener((MessageListener<String, ProductCreatedEvent>) records::add);
        container.start();
        ContainerTestUtils.waitForAssignment(container, embeddedKafkaBroker.getPartitionsPerTopic());
    }

    @Test
    void testCreateProduct_whenGivenValidProductDetails_successfullySendKafkaMessage() throws ExecutionException, InterruptedException {
        //Arrange
        String title = "LG";
        BigDecimal price = new BigDecimal(600);
        Integer quantity = 1;

        CreateProductDto createProductDto = new CreateProductDto(title, price, quantity);

        //Act
        productService.createProduct(createProductDto);

        //Assert
        ConsumerRecord<String, ProductCreatedEvent> message = records.poll(3000, TimeUnit.MILLISECONDS);
        assertNotNull(message);
        assertNotNull(message.key());
        ProductCreatedEvent productCreatedEvent = message.value();
        assertEquals(createProductDto.getQuantity(), productCreatedEvent.getQuantity());
        assertEquals(createProductDto.getTitle(), productCreatedEvent.getTitle());
        assertEquals(createProductDto.getPrice(), productCreatedEvent.getPrice());

    }

    @Test
    void shouldReturnValidUuidAndUseItAsKafkaKey() throws ExecutionException, InterruptedException {
        CreateProductDto createProductDto = new CreateProductDto("Samsung", new BigDecimal("899.99"), 2);

        String productId = productService.createProduct(createProductDto);
        UUID.fromString(productId);

        ConsumerRecord<String, ProductCreatedEvent> message = records.poll(3000, TimeUnit.MILLISECONDS);
        assertNotNull(message);
        assertEquals(productId, message.key());
        assertEquals(productId, message.value().getProductId());
    }

    @Test
    void shouldPublishToExpectedTopic() throws ExecutionException, InterruptedException {
        CreateProductDto createProductDto = new CreateProductDto("Sony", new BigDecimal("450.00"), 1);

        productService.createProduct(createProductDto);

        ConsumerRecord<String, ProductCreatedEvent> message = records.poll(3000, TimeUnit.MILLISECONDS);
        assertNotNull(message);
        assertEquals(environment.getProperty("product-created-events-topic-name"), message.topic());
    }

    @Test
    void shouldIncludeMessageIdHeader() throws ExecutionException, InterruptedException {
        CreateProductDto createProductDto = new CreateProductDto("Asus", new BigDecimal("1200.00"), 1);

        productService.createProduct(createProductDto);

        ConsumerRecord<String, ProductCreatedEvent> message = records.poll(3000, TimeUnit.MILLISECONDS);
        assertNotNull(message);
        Header messageIdHeader = message.headers().lastHeader("messageId");
        assertNotNull(messageIdHeader);
        assertNotNull(messageIdHeader.value());
        assertEquals(false, new String(messageIdHeader.value()).isBlank());
    }

    @Test
    void shouldPublishDistinctEventsForMultipleCreates() throws ExecutionException, InterruptedException {
        CreateProductDto first = new CreateProductDto("Dell", new BigDecimal("700.00"), 3);
        CreateProductDto second = new CreateProductDto("HP", new BigDecimal("800.00"), 4);

        String firstProductId = productService.createProduct(first);
        String secondProductId = productService.createProduct(second);

        ConsumerRecord<String, ProductCreatedEvent> firstMessage = records.poll(3000, TimeUnit.MILLISECONDS);
        ConsumerRecord<String, ProductCreatedEvent> secondMessage = records.poll(3000, TimeUnit.MILLISECONDS);

        assertNotNull(firstMessage);
        assertNotNull(secondMessage);
        assertEquals(false, firstProductId.equals(secondProductId));
        assertEquals(false, firstMessage.key().equals(secondMessage.key()));
    }

    @Test
    void shouldPreservePricePrecision() throws ExecutionException, InterruptedException {
        CreateProductDto createProductDto = new CreateProductDto("Acer", new BigDecimal("600.10"), 1);

        productService.createProduct(createProductDto);

        ConsumerRecord<String, ProductCreatedEvent> message = records.poll(3000, TimeUnit.MILLISECONDS);
        assertNotNull(message);
        assertEquals(createProductDto.getPrice(), message.value().getPrice());
    }

    private Map<String, Object> getConsumerProperties() {
        return Map.of(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString(),
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class,
                ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JacksonJsonDeserializer.class,
                ConsumerConfig.GROUP_ID_CONFIG, environment.getProperty("spring.kafka.consumer.group-id") + "-service-it",
                JacksonJsonDeserializer.TRUSTED_PACKAGES, environment.getProperty("spring.kafka.consumer.properties.spring.json.trusted.packages"),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, environment.getProperty("spring.kafka.consumer.auto-offset-reset")
        );
    }

    @AfterAll
    void tearDown() {
        container.stop();
    }
}
