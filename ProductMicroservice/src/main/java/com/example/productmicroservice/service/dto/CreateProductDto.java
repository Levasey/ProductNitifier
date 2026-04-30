package com.example.productmicroservice.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class CreateProductDto {
    @NotBlank(message = "title is required")
    private String title;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.01", inclusive = true, message = "price must be at least 0.01")
    private BigDecimal price;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private Integer quantity;

    public CreateProductDto() {}

    public CreateProductDto(String title, BigDecimal price, Integer quantity) {
        this.title = title;
        this.price = price;
        this.quantity = quantity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
