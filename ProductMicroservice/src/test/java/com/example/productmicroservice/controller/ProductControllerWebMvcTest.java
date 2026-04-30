package com.example.productmicroservice.controller;

import com.example.productmicroservice.exception.RestExceptionHandler;
import com.example.productmicroservice.service.ProductService;
import com.example.productmicroservice.service.dto.CreateProductDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.concurrent.ExecutionException;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerWebMvcTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void postProduct_whenValidBody_returnsCreatedAndProductId() throws Exception {
        when(productService.createProduct(any(CreateProductDto.class))).thenReturn("product-uuid-1");

        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Book\",\"price\":15.00,\"quantity\":4}"))
                .andExpect(status().isCreated())
                .andExpect(content().string("product-uuid-1"));

        verify(productService).createProduct(any(CreateProductDto.class));
    }

    @Test
    void postProduct_whenServiceFails_returnsInternalServerErrorWithErrorMessage() throws Exception {
        when(productService.createProduct(any(CreateProductDto.class)))
                .thenThrow(new ExecutionException("failed", new RuntimeException("kafka down")));

        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Book\",\"price\":15.00,\"quantity\":4}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(containsString("failed")));
    }

    @Test
    void postProduct_whenMalformedJson_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid request body"));
    }

    @Test
    void postProduct_whenBlankTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"price\":10,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("title is required"));
    }

    @Test
    void postProduct_whenPriceTooLow_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"price\":0,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.price").value("price must be at least 0.01"));
    }

    @Test
    void postProduct_whenQuantityNotPositive_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"price\":10,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.quantity").value("quantity must be positive"));
    }

    @Test
    void postProduct_whenMissingRequiredFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/product")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").exists())
                .andExpect(jsonPath("$.price").exists())
                .andExpect(jsonPath("$.quantity").exists());
    }
}
