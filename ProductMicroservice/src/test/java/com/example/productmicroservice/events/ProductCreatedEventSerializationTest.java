package com.example.productmicroservice.events;

import com.example.core.events.ProductCreatedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductCreatedEventSerializationTest {

    @Test
    void jsonRoundTrip_preservesFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ProductCreatedEvent original =
                new ProductCreatedEvent("pid-1", "Widget", new BigDecimal("12.34"), 7);

        String json = mapper.writeValueAsString(original);
        ProductCreatedEvent restored = mapper.readValue(json, ProductCreatedEvent.class);

        assertEquals(original.getProductId(), restored.getProductId());
        assertEquals(original.getTitle(), restored.getTitle());
        assertEquals(original.getPrice(), restored.getPrice());
        assertEquals(original.getQuantity(), restored.getQuantity());
    }
}
