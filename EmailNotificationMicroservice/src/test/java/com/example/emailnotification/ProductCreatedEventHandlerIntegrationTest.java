package com.example.emailnotification;

import com.example.core.events.ProductCreatedEvent;
import com.example.emailnotification.persistence.entity.ProcessedEventEntity;
import com.example.emailnotification.persistence.repository.ProcessedEventRepository;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@EmbeddedKafka
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
public class ProductCreatedEventHandlerIntegrationTest {

    @MockitoBean
    ProcessedEventRepository processedEventRepository;

    @MockitoBean
    RestTemplate restTemplate;

    @Autowired
    KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${mockservice.response-url}")
    String mockServiceResponseUrl;

    @Test
    public void productCreatedEventHandler_OnProductCreated_HandlesEvent() throws ExecutionException, InterruptedException {
        //Arrange
        ProductCreatedEvent productCreatedEvent = new ProductCreatedEvent();
        productCreatedEvent.setPrice(new BigDecimal(10));
        productCreatedEvent.setProductId(UUID.randomUUID().toString());
        productCreatedEvent.setQuantity(1);
        productCreatedEvent.setTitle("Test product");

        String messageId = UUID.randomUUID().toString();
        String messageKey = productCreatedEvent.getProductId();

        ProducerRecord<String, Object> record = new ProducerRecord<>(
                "product-created-events-topic",
                messageKey,
                productCreatedEvent
        );

        record.headers().add("messageId", messageId.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        when(processedEventRepository.findByMessageId(anyString())).thenReturn(null);
        when(processedEventRepository.save(any(ProcessedEventEntity.class))).thenReturn(null);

        String responseBody = "{\"key\":\"value\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, headers, HttpStatus.OK);

        when(restTemplate.exchange(
                any(String.class),
                any(HttpMethod.class),
                isNull(),
                eq(String.class)
        )).thenReturn(responseEntity);

        //Act
        kafkaTemplate.send(record).get();

        verify(restTemplate, timeout(5000)).exchange(
                eq(mockServiceResponseUrl),
                eq(HttpMethod.GET),
                isNull(),
                eq(String.class));
        verify(processedEventRepository).save(argThat(entity ->
                messageId.equals(entity.getMessageId())
                        && productCreatedEvent.getProductId().equals(entity.getProductId())));
    }
}
