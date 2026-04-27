package com.example.productmicroservice.service;

import com.example.core.events.ProductCreatedEvent;
import com.example.productmicroservice.service.dto.CreateProductDto;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceImplTest {

    private final KafkaTemplate<String, ProductCreatedEvent> kafkaTemplate = mock(KafkaTemplate.class);
    private final ProductServiceImpl productService = new ProductServiceImpl(kafkaTemplate);

    @Test
    void createProduct_whenKafkaSendSucceeds_returnsGeneratedProductIdAndPublishesEvent() throws Exception {
        SendResult<String, ProductCreatedEvent> sendResult = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        when(metadata.topic()).thenReturn("product-created-events-topic");
        when(metadata.partition()).thenReturn(1);
        when(metadata.offset()).thenReturn(10L);
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(sendResult));

        String productId = productService.createProduct(new CreateProductDto("Phone", new BigDecimal("99.99"), 2));

        assertNotNull(productId);
        verify(kafkaTemplate).send(any(ProducerRecord.class));
    }

    @Test
    void createProduct_whenKafkaSendFails_propagatesException() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka error")));

        assertThrows(ExecutionException.class,
                () -> productService.createProduct(new CreateProductDto("Phone", new BigDecimal("99.99"), 2)));
    }
}
