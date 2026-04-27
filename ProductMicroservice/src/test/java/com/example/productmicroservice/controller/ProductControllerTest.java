package com.example.productmicroservice.controller;

import com.example.productmicroservice.service.ProductService;
import com.example.productmicroservice.service.dto.CreateProductDto;
import com.example.productmicroservice.exception.ErrorMessage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductControllerTest {

    private final ProductService productService = mock(ProductService.class);
    private final ProductController controller = new ProductController(productService);

    @Test
    void createProduct_whenServiceSucceeds_returnsCreatedAndProductId() throws Exception {
        CreateProductDto request = new CreateProductDto("Book", new BigDecimal("15.00"), 4);
        when(productService.createProduct(request)).thenReturn("product-1");

        ResponseEntity<Object> response = controller.createProduct(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("product-1", response.getBody());
    }

    @Test
    void createProduct_whenServiceFails_returnsInternalServerError() throws Exception {
        CreateProductDto request = new CreateProductDto("Book", new BigDecimal("15.00"), 4);
        when(productService.createProduct(request)).thenThrow(new ExecutionException("failed", new RuntimeException()));

        ResponseEntity<Object> response = controller.createProduct(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertInstanceOf(ErrorMessage.class, response.getBody());
    }
}
