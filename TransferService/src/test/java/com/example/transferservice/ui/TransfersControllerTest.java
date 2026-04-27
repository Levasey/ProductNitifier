package com.example.transferservice.ui;

import com.example.transferservice.model.TransferRestModel;
import com.example.transferservice.service.TransferService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransfersControllerTest {

    private final TransferService transferService = mock(TransferService.class);
    private final TransfersController controller = new TransfersController(transferService);

    @Test
    void transfer_whenServiceReturnsTrue_returnsTrue() {
        TransferRestModel request = request();
        when(transferService.transfer(request)).thenReturn(true);

        assertTrue(controller.transfer(request));
    }

    @Test
    void transfer_whenServiceReturnsFalse_returnsFalse() {
        TransferRestModel request = request();
        when(transferService.transfer(request)).thenReturn(false);

        assertFalse(controller.transfer(request));
    }

    private TransferRestModel request() {
        TransferRestModel request = new TransferRestModel();
        request.setSenderId("s-1");
        request.setRecipientId("r-1");
        request.setAmount(new BigDecimal("12.50"));
        return request;
    }
}
