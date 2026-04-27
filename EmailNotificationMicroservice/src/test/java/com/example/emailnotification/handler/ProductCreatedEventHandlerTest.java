package com.example.emailnotification.handler;

import com.example.core.events.ProductCreatedEvent;
import com.example.emailnotification.exception.NonRetryableException;
import com.example.emailnotification.exception.RetryableException;
import com.example.emailnotification.persistence.entity.ProcessedEventEntity;
import com.example.emailnotification.persistence.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductCreatedEventHandlerTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final ProcessedEventRepository processedEventRepository = mock(ProcessedEventRepository.class);
    private final ProductCreatedEventHandler handler =
            new ProductCreatedEventHandler(restTemplate, "http://mockservice/response", processedEventRepository);

    @Test
    void handle_whenMessageAlreadyProcessed_skipsExternalCallAndSave() {
        String messageId = "msg-1";
        when(processedEventRepository.findByMessageId(messageId)).thenReturn(new ProcessedEventEntity());

        handler.handle(sampleEvent(), messageId, "product-1");

        verify(processedEventRepository, never()).save(any(ProcessedEventEntity.class));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void handle_whenRemoteServiceUnavailable_throwsRetryableException() {
        String messageId = "msg-2";
        when(processedEventRepository.findByMessageId(messageId)).thenReturn(null);
        when(restTemplate.exchange(eq("http://mockservice/response"), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        assertThrows(RetryableException.class, () -> handler.handle(sampleEvent(), messageId, "product-1"));
        verify(processedEventRepository, never()).save(any(ProcessedEventEntity.class));
    }

    @Test
    void handle_whenUnexpectedException_throwsNonRetryableException() {
        String messageId = "msg-3";
        when(processedEventRepository.findByMessageId(messageId)).thenReturn(null);
        when(restTemplate.exchange(eq("http://mockservice/response"), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenThrow(new IllegalArgumentException("bad response"));

        assertThrows(NonRetryableException.class, () -> handler.handle(sampleEvent(), messageId, "product-1"));
        verify(processedEventRepository, never()).save(any(ProcessedEventEntity.class));
    }

    @Test
    void handle_whenSaveFails_throwsNonRetryableException() {
        String messageId = "msg-4";
        when(processedEventRepository.findByMessageId(messageId)).thenReturn(null);
        when(restTemplate.exchange(eq("http://mockservice/response"), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
        when(processedEventRepository.save(any(ProcessedEventEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(NonRetryableException.class, () -> handler.handle(sampleEvent(), messageId, "product-1"));
    }

    @Test
    void handle_whenSuccessful_persistsProcessedEvent() {
        String messageId = "msg-5";
        ProductCreatedEvent event = sampleEvent();
        when(processedEventRepository.findByMessageId(messageId)).thenReturn(null);
        when(restTemplate.exchange(eq("http://mockservice/response"), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        assertDoesNotThrow(() -> handler.handle(event, messageId, event.getProductId()));

        verify(processedEventRepository).save(any(ProcessedEventEntity.class));
    }

    private ProductCreatedEvent sampleEvent() {
        return new ProductCreatedEvent("product-1", "Phone", new BigDecimal("599.99"), 3);
    }
}
