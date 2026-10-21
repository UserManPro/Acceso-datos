package com.aadtex.aadtex.model;
import java.math.BigDecimal;
import java.time.LocalDate;
public record Sale(String storeId, String storeName, BigDecimal amount, String category, LocalDate date) { }
