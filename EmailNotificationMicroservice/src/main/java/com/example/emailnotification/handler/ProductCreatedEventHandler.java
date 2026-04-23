package com.example.emailnotification.handler;

import com.example.core.ProductCreatedEvent;
import com.example.emailnotification.exception.NonRetryableException;
import com.example.emailnotification.exception.RetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@KafkaListener(topics = "product-created-events-topic", containerFactory = "productCreatedEventKafkaListener")
public class ProductCreatedEventHandler {

    private final RestTemplate restTemplate;
    private final String mockServiceResponseUrl;
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    public ProductCreatedEventHandler(
            RestTemplate restTemplate,
            @Value("${mockservice.response-url}") String mockServiceResponseUrl) {
        this.restTemplate = restTemplate;
        this.mockServiceResponseUrl = mockServiceResponseUrl;
    }

    @KafkaHandler
    public void handle(ProductCreatedEvent productCreatedEvent) {
        LOGGER.info("Product created event received: {}", productCreatedEvent.getTitle());

        try {
            ResponseEntity<String> response = restTemplate.exchange(mockServiceResponseUrl, HttpMethod.GET, null, String.class);
            if (response.getStatusCode().value() == HttpStatus.OK.value()) {
                LOGGER.info("Received response: {}", response.getBody());
            }
        } catch (ResourceAccessException e) {
            LOGGER.error(e.getMessage());
            throw new RetryableException(e);
        } catch (HttpServerErrorException e) {
            LOGGER.error(e.getMessage());
            throw new RetryableException(e);
        } catch (Exception e) {
            LOGGER.error(e.getMessage());
            throw new NonRetryableException(e);
        }

    }
}
