package com.aadtex.aadtex.model;
import java.math.BigDecimal;
import java.time.Instant;
public record BatchSummary(String id, String source, Instant processedAt, int recordCount, BigDecimal total) { }
