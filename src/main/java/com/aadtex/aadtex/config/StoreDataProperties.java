package com.aadtex.aadtex.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StoreDataProperties {
    private final Path root;
    public StoreDataProperties(@Value("${aadtex.data-root:C:/workspace/store_data}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }
    public Path root() { return root; }
    public Path input() { return root.resolve("input"); }
    public Path key() { return root.resolve("keys").resolve("AadTexSecret.key"); }
    public Path processed() { return root.resolve("output").resolve("processed"); }
    public Path signatures() { return processed().resolve("signatures"); }
    public Path errors() { return root.resolve("output").resolve("error"); }
    public Path bucket() { return root.resolve("output").resolve("bucket").resolve("aadtex-daily-reports").resolve("2026").resolve("09"); }
}
