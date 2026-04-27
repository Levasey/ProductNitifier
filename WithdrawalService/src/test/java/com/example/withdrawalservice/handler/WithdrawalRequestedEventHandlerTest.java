package com.example.withdrawalservice.handler;

import com.example.core.events.WithdrawalRequestedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class WithdrawalRequestedEventHandlerTest {

    private final WithdrawalRequestedEventHandler handler = new WithdrawalRequestedEventHandler();

    @Test
    void handle_whenValidEventProvided_doesNotThrow() {
        WithdrawalRequestedEvent event =
                new WithdrawalRequestedEvent("sender-1", "recipient-1", new BigDecimal("10.50"));

        assertDoesNotThrow(() -> handler.handle(event));
    }
}
