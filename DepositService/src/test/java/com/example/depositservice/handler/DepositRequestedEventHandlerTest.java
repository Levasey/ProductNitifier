package com.example.depositservice.handler;

import com.example.core.events.DepositRequestedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class DepositRequestedEventHandlerTest {

    private final DepositRequestedEventHandler handler = new DepositRequestedEventHandler();

    @Test
    void handle_whenValidEventProvided_doesNotThrow() {
        DepositRequestedEvent event = new DepositRequestedEvent("sender-1", "recipient-1", new BigDecimal("10.50"));

        assertDoesNotThrow(() -> handler.handle(event));
    }
}
