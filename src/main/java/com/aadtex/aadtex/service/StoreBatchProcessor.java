package com.aadtex.aadtex.service;

import com.aadtex.aadtex.config.StoreDataProperties;
import com.aadtex.aadtex.model.BatchSummary;
import com.aadtex.aadtex.model.Sale;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Rectangle;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.math.BigDecimal;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StoreBatchProcessor {
    private static final Logger log = LoggerFactory.getLogger(StoreBatchProcessor.class);
    private static final String HEADER = CsvSalesService.HEADER;
    private final StoreDataProperties paths;
    private final CsvSalesService csv;
    private final CryptoService crypto;
    private final CloudBucketService bucket;

    public StoreBatchProcessor(StoreDataProperties paths, CsvSalesService csv, CryptoService crypto, CloudBucketService bucket) {
        this.paths = paths; this.csv = csv; this.crypto = crypto; this.bucket = bucket;
    }

    public synchronized void processStoreBatches() {
        try {
            Files.createDirectories(paths.input());
            Files.createDirectories(paths.processed());
            Files.createDirectories(paths.signatures());
            Files.createDirectories(paths.errors());
            Files.createDirectories(paths.bucket());
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(paths.input())) {
                for (Path file : stream) {
                    if (!Files.isRegularFile(file) || !isSupported(file)) continue;
                    try {
                        if (Files.size(file) == 0) { isolate(file); continue; }
                        String ext = extension(file);
                        if (ext.equals(".csv")) processCsvFile(file);
                        else if (ext.equals(".enc")) processEncryptedFile(file);
                        else if (ext.equals(".zip")) processZipFile(file);
                        else if (ext.equals(".jpg")) processOrphanSignature(file);
                    } catch (Exception e) {
                        log.error("No se pudo procesar {}: {}", file.getFileName(), e.getMessage());
                        if (Files.exists(file)) isolate(file);
                    }
                }
            }
        } catch (IOException e) { log.error("No se pudo preparar o recorrer la carpeta de entrada: {}", e.getMessage()); }
    }

    private void processCsvFile(Path file) throws IOException {
        List<Sale> sales;
        try (InputStream in = Files.newInputStream(file)) { sales = csv.read(in, file.getFileName().toString()); }
        String signatureName = signatureFor(file.getFileName().toString());
        Path signature = signatureName.isBlank() ? null : paths.input().resolve(signatureName);
        if (signature != null && Files.exists(signature)) copyJpeg(signature, paths.signatures().resolve(signatureName));
        publish(file.getFileName().toString(), sales);
        move(file, paths.processed().resolve(file.getFileName()));
        if (signature != null && Files.exists(signature)) move(signature, paths.processed().resolve(signatureName));
    }

    private void processEncryptedFile(Path file) throws IOException {
        byte[] cipherBytes = Files.readAllBytes(file);
        byte[] plain = crypto.decrypt(cipherBytes, paths.key());
        List<Sale> sales;
        try (ByteArrayInputStream in = new ByteArrayInputStream(plain)) { sales = csv.read(in, file.getFileName().toString()); }
        finally { java.util.Arrays.fill(plain, (byte) 0); }
        String sigName = "signature_financial.jpg";
        Path signature = paths.input().resolve(sigName);
        if (Files.exists(signature)) copyJpeg(signature, paths.signatures().resolve(sigName));
        publish(file.getFileName().toString(), sales);
        move(file, paths.processed().resolve(file.getFileName()));
        if (Files.exists(signature)) move(signature, paths.processed().resolve(sigName));
    }

    private void processZipFile(Path file) throws IOException {
        List<Sale> sales = null;
        boolean signatureFound = false;
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory() || entry.getName().contains("/") || entry.getName().contains("\\") || entry.getName().contains(".."))
                    throw new IOException("El ZIP contiene una ruta de entrada no permitida.");
                if (entry.getName().equals("daily_sales_usa.csv")) {
                    ByteArrayOutputStream bytes = new ByteArrayOutputStream(); int n;
                    while ((n = zip.read(buffer)) != -1) bytes.write(buffer, 0, n);
                    sales = csv.read(new ByteArrayInputStream(bytes.toByteArray()), entry.getName());
                } else if (entry.getName().equals("signature_usa.jpg")) {
                    Path target = paths.signatures().resolve("signature_usa.jpg");
                    try (BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                        int n; byte[] prefix = new byte[3]; int prefixLength = 0;
                        while ((n = zip.read(buffer)) != -1) {
                            int toCopy = Math.min(3 - prefixLength, n);
                            if (toCopy > 0) { System.arraycopy(buffer, 0, prefix, prefixLength, toCopy); prefixLength += toCopy; }
                            out.write(buffer, 0, n);
                        }
                        if (prefixLength < 3 || (prefix[0]&255) != 255 || (prefix[1]&255) != 216 || (prefix[2]&255) != 255)
                            throw new IOException("La firma incluida en el ZIP no parece JPEG.");
                    }
                    if (!isValidJpeg(target)) {
                        Files.deleteIfExists(target);
                        throw new IOException("La firma incluida en el ZIP no es una imagen JPEG válida.");
                    }
                    signatureFound = true;
                }
                zip.closeEntry();
            }
        }
        if (sales == null || !signatureFound) throw new IOException("El ZIP debe incluir CSV y firma con los nombres establecidos.");
        publish(file.getFileName().toString(), sales);
        move(file, paths.processed().resolve(file.getFileName()));
    }

    private void processOrphanSignature(Path file) throws IOException {
        String name = file.getFileName().toString();
        if (name.equals("signature_spain.jpg") || name.equals("signature_china.jpg") || name.equals("signature_financial.jpg") || name.equals("signature_usa.jpg")) {
            copyJpeg(file, paths.signatures().resolve(name));
            move(file, paths.processed().resolve(file.getFileName()));
        } else throw new IOException("Nombre de firma JPG no reconocido.");
    }

    private void copyJpeg(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (BufferedInputStream in = new BufferedInputStream(Files.newInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
            byte[] buffer = new byte[4096];
            int n; byte[] prefix = new byte[3]; int prefixLength = 0;
            while ((n = in.read(buffer)) != -1) {
                int toCopy = Math.min(3 - prefixLength, n);
                if (toCopy > 0) { System.arraycopy(buffer, 0, prefix, prefixLength, toCopy); prefixLength += toCopy; }
                out.write(buffer, 0, n);
            }
            if (prefixLength < 3 || (prefix[0]&255) != 255 || (prefix[1]&255) != 216 || (prefix[2]&255) != 255)
                throw new IOException("El fichero no tiene cabecera JPEG válida: " + source.getFileName());
        }
        if (!isValidJpeg(target)) {
            Files.deleteIfExists(target);
            throw new IOException("El fichero no contiene una imagen JPEG legible: " + source.getFileName());
        }
    }

    /** Lee solo la estructura y dimensiones; no decodifica la imagen completa en memoria. */
    private static boolean isValidJpeg(Path image) {
        try (ImageInputStream input = ImageIO.createImageInputStream(image.toFile())) {
            if (input == null) return false;
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0) return false;
                var parameters = reader.getDefaultReadParam();
                parameters.setSourceRegion(new Rectangle(0, 0, Math.min(width, 8), Math.min(height, 8)));
                return reader.read(0, parameters) != null;
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException e) { return false; }
    }

    private void publish(String source, List<Sale> sales) throws IOException {
        BigDecimal total = sales.stream().map(Sale::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        String id = source.replaceAll("[^A-Za-z0-9_-]", "_") + "_" + UUID.randomUUID();
        Path saved = bucket.write(new BatchSummary(id, source, Instant.now(), sales.size(), total));
        if (source.toLowerCase(Locale.ROOT).contains("financial"))
            log.info("Lote financiero procesado: registros={}, resumen={}", sales.size(), saved.getFileName());
        else log.info("Lote procesado: origen={}, registros={}, total={}, resumen={}", source, sales.size(), total.toPlainString(), saved.getFileName());
    }

    private void isolate(Path file) {
        log.error("[ALERT EMAIL SENT -> soporte@aadtex.com] CRITICAL: Empty/Corrupt batch file: {}", file.getFileName());
        try { move(file, paths.errors().resolve(file.getFileName())); }
        catch (IOException e) { log.error("No se pudo aislar {} en la carpeta de errores: {}", file.getFileName(), e.getMessage()); }
    }

    private static void move(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(source, target, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static String signatureFor(String name) {
        if (name.contains("spain")) return "signature_spain.jpg";
        if (name.contains("china")) return "signature_china.jpg";
        return "";
    }
    private static String extension(Path path) { String n=path.getFileName().toString().toLowerCase(Locale.ROOT); int i=n.lastIndexOf('.'); return i<0?"":n.substring(i); }
    private static boolean isSupported(Path p) { return List.of(".csv", ".enc", ".zip", ".jpg").contains(extension(p)); }
}
