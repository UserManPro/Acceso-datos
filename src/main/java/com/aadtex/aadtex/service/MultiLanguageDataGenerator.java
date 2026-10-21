package com.aadtex.aadtex.service;

import com.aadtex.aadtex.config.StoreDataProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aadtex.generator.enabled", havingValue = "true", matchIfMissing = true)
public class MultiLanguageDataGenerator implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(MultiLanguageDataGenerator.class);
    private static final String KEY = "AadTexSecret2026";
    private static final String HEADER = "StoreID,StoreName,SalesAmount,Category,TransactionDate\n";
    private static final byte[] JPEG = createJpegFixture();
    private final StoreDataProperties paths;
    public MultiLanguageDataGenerator(StoreDataProperties paths) { this.paths = paths; }

    @Override public void run(String... args) throws Exception {
        Files.createDirectories(paths.input());
        Files.createDirectories(paths.key().getParent());
        createIfMissing(paths.key(), KEY.getBytes(StandardCharsets.UTF_8));
        createIfMissing(paths.input().resolve("daily_sales_spain.csv"), (HEADER +
                "ZARA_ES_001,Zara Puerta del Sol Madrid,14500.50,WOMEN,2026-09-13\n" +
                "ZARA_ES_001,Zara Puerta del Sol Madrid,3600.30,MEN,2026-09-13\n" +
                "ZARA_ES_001,Zara Gran Vía Madrid,2000.00,KIDS,2026-09-13\n").getBytes(StandardCharsets.UTF_8));
        createIfMissing(paths.input().resolve("daily_sales_china.csv"), (HEADER +
                "ZARA_CH_001,Zara 上海南京东路店,28900.00,WOMEN,2026-09-13\n" +
                "ZARA_CH_001,Zara 上海南京东路店,12400.80,MEN,2026-09-13\n").getBytes(StandardCharsets.UTF_8));
        createIfMissing(paths.input().resolve("daily_sales_financial.enc"), encrypt((HEADER +
                "ZARA_CENTRAL_FIN,Central Financial Office,95000.00,FINANCIAL_REPORT,2026-09-13\n").getBytes(StandardCharsets.UTF_8)));
        createIfMissing(paths.input().resolve("daily_sales_broken_store.csv"), new byte[0]);
        createIfMissing(paths.input().resolve("signature_spain.jpg"), JPEG);
        createIfMissing(paths.input().resolve("signature_china.jpg"), JPEG);
        createIfMissing(paths.input().resolve("signature_financial.jpg"), JPEG);
        createIfMissing(paths.input().resolve("batch_usa.zip"), usaZip());
        log.info("Datos de prueba preparados en {} (solo se crean archivos que aún no existen).", paths.input());
    }

    private static byte[] encrypt(byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        return cipher.doFinal(plain);
    }
    private static byte[] createJpegFixture() {
        try {
            BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "jpg", bytes)) throw new IllegalStateException("No hay un codificador JPEG disponible.");
            return bytes.toByteArray();
        } catch (IOException e) { throw new IllegalStateException("No se pudo generar la firma JPEG simulada.", e); }
    }
    private static byte[] usaZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("daily_sales_usa.csv"));
            zip.write((HEADER + "ZARA_US_001,Zara Times Square New York,42000.00,WOMEN,2026-09-13\n" +
                    "ZARA_US_001,Zara Times Square New York,18850.75,MEN,2026-09-13\n" +
                    "ZARA_US_001,Zara 5th Avenue New York,7600.00,KIDS,2026-09-13\n").getBytes(StandardCharsets.UTF_8));
            zip.closeEntry(); zip.putNextEntry(new ZipEntry("signature_usa.jpg")); zip.write(JPEG); zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static void createIfMissing(Path path, byte[] bytes) throws IOException {
        if (Files.exists(path)) return;
        Files.createDirectories(path.getParent());
        try { Files.write(path, bytes, java.nio.file.StandardOpenOption.CREATE_NEW); }
        catch (java.nio.file.FileAlreadyExistsException ignored) { /* Otro arranque lo creó simultáneamente. */ }
    }
}
