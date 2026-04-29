package com.example.transferservice.service;

import com.example.transferservice.error.TransferServiceException;
import com.example.transferservice.model.TransferRestModel;
import com.example.transferservice.persistance.TransferEntity;
import com.example.transferservice.persistance.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TransferServiceImplTest {

    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final Environment environment = mock(Environment.class);
    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final TransferRepository transferRepository = mock(TransferRepository.class);
    private final TransferServiceImpl transferService =
            new TransferServiceImpl(kafkaTemplate, environment, restTemplate, transferRepository);

    @Test
    void transfer_whenRemoteServiceSucceeds_sendsWithdrawalAndDepositEvents() {
        TransferRestModel request = transferRequest();
        when(environment.getProperty("withdraw-money-topic", "withdraw-money-topic")).thenReturn("withdraw-topic");
        when(environment.getProperty("deposit-money-topic", "deposit-money-topic")).thenReturn("deposit-topic");
        when(environment.getProperty("mock-service.url", "http://localhost:8090/response/200"))
                .thenReturn("http://mockservice/response/200");
        when(restTemplate.exchange(eq("http://mockservice/response/200"), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        boolean result = transferService.transfer(request);

        assertTrue(result);
        verify(transferRepository).save(any(TransferEntity.class));
        verify(kafkaTemplate).send(eq("withdraw-topic"), any());
        verify(kafkaTemplate).send(eq("deposit-topic"), any());
    }

    @Test
    void transfer_whenRemoteServiceUnavailable_wrapsExceptionAndSkipsDeposit() {
        TransferRestModel request = transferRequest();
        when(environment.getProperty("withdraw-money-topic", "withdraw-money-topic")).thenReturn("withdraw-topic");
        when(environment.getProperty("deposit-money-topic", "deposit-money-topic")).thenReturn("deposit-topic");
        when(environment.getProperty("mock-service.url", "http://localhost:8090/response/200"))
                .thenReturn("http://mockservice/response/503");
        when(restTemplate.exchange(eq("http://mockservice/response/503"), eq(HttpMethod.GET), eq(null), eq(String.class)))
                .thenReturn(new ResponseEntity<>("down", HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(TransferServiceException.class, () -> transferService.transfer(request));

        verify(transferRepository).save(any(TransferEntity.class));
        verify(kafkaTemplate).send(eq("withdraw-topic"), any());
        verify(kafkaTemplate, never()).send(eq("deposit-topic"), any());
    }

    @Test
    void transfer_whenWithdrawalPublishFails_wrapsException() {
        TransferRestModel request = transferRequest();
        when(environment.getProperty("withdraw-money-topic", "withdraw-money-topic")).thenReturn("withdraw-topic");
        when(kafkaTemplate.send(eq("withdraw-topic"), any())).thenThrow(new RuntimeException("kafka down"));

        assertThrows(TransferServiceException.class, () -> transferService.transfer(request));
        verify(transferRepository).save(any(TransferEntity.class));
    }

    @Test
    void transfer_whenSaveFails_wrapsExceptionAndSkipsKafkaCalls() {
        TransferRestModel request = transferRequest();
        when(transferRepository.save(any(TransferEntity.class))).thenThrow(new RuntimeException("db down"));

        assertThrows(TransferServiceException.class, () -> transferService.transfer(request));

        verify(transferRepository, times(1)).save(any(TransferEntity.class));
        verify(kafkaTemplate, never()).send(any(String.class), any());
    }

    private TransferRestModel transferRequest() {
        TransferRestModel request = new TransferRestModel();
        request.setSenderId("sender-1");
        request.setRecipientId("recipient-1");
        request.setAmount(new BigDecimal("25.00"));
        return request;
    }
}
