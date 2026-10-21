package com.aadtex.aadtex.service;

import com.aadtex.aadtex.config.StoreDataProperties;
import com.aadtex.aadtex.model.BatchSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class CloudBucketService {
    private final StoreDataProperties paths;
    public CloudBucketService(StoreDataProperties paths) { this.paths = paths; }

    public Path write(BatchSummary summary) throws IOException {
        Files.createDirectories(paths.bucket());
        String json = "{\n" +
                "  \"id\": \"" + escape(summary.id()) + "\",\n" +
                "  \"source\": \"" + escape(summary.source()) + "\",\n" +
                "  \"processedAt\": \"" + DateTimeFormatter.ISO_INSTANT.format(summary.processedAt()) + "\",\n" +
                "  \"recordCount\": " + summary.recordCount() + ",\n" +
                "  \"total\": " + summary.total().toPlainString() + "\n}\n";
        Path target = paths.bucket().resolve("summary_batch_" + safe(summary.id()) + ".json");
        int suffix = 1;
        while (Files.exists(target)) target = paths.bucket().resolve("summary_batch_" + safe(summary.id()) + "_" + suffix++ + ".json");
        return Files.writeString(target, json, StandardCharsets.UTF_8);
    }
    private static String safe(String value) { return value.replaceAll("[^A-Za-z0-9_-]", "_"); }
    private static String escape(String value) { return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r"); }
}
