package com.example.productmicroservice.service;

import com.example.core.events.ProductCreatedEvent;
import com.example.productmicroservice.service.dto.CreateProductDto;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
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

        CreateProductDto dto = new CreateProductDto("Phone", new BigDecimal("99.99"), 2);
        String productId = productService.createProduct(dto);

        assertNotNull(productId);
        UUID.fromString(productId);

        ArgumentCaptor<ProducerRecord<String, ProductCreatedEvent>> captor = ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate).send(captor.capture());
        ProducerRecord<String, ProductCreatedEvent> record = captor.getValue();

        assertEquals("product-created-events-topic", record.topic());
        assertEquals(productId, record.key());
        ProductCreatedEvent value = record.value();
        assertEquals(productId, value.getProductId());
        assertEquals(dto.getTitle(), value.getTitle());
        assertEquals(dto.getPrice(), value.getPrice());
        assertEquals(dto.getQuantity(), value.getQuantity());

        Header header = record.headers().lastHeader("messageId");
        assertNotNull(header);
        assertNotNull(UUID.fromString(new String(header.value(), StandardCharsets.UTF_8)));
    }

    @Test
    void createProduct_whenKafkaSendFails_propagatesException() {
        when(kafkaTemplate.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka error")));

        assertThrows(ExecutionException.class,
                () -> productService.createProduct(new CreateProductDto("Phone", new BigDecimal("99.99"), 2)));
    }

    @Test
    void createProduct_whenInterruptedWhileWaitingOnSend_propagatesInterruptedException() throws Exception {
        CompletableFuture<SendResult<String, ProductCreatedEvent>> future = mock(CompletableFuture.class);
        when(future.get()).thenThrow(new InterruptedException("simulated"));
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenReturn(future);

        assertThrows(InterruptedException.class,
                () -> productService.createProduct(new CreateProductDto("Phone", new BigDecimal("99.99"), 2)));
    }
}
