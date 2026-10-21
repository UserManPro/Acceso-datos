package com.aadtex.aadtex;

import com.aadtex.aadtex.config.StoreDataProperties;
import com.aadtex.aadtex.scheduler.StoreIngestionScheduler;
import com.aadtex.aadtex.scheduler.CronStoreIngestionScheduler;
import com.aadtex.aadtex.service.CloudBucketService;
import com.aadtex.aadtex.service.CryptoService;
import com.aadtex.aadtex.service.CsvSalesService;
import com.aadtex.aadtex.service.MultiLanguageDataGenerator;
import com.aadtex.aadtex.service.StoreBatchProcessor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.annotation.Scheduled;
import static org.junit.jupiter.api.Assertions.*;

class AadtexApplicationTests {
    @TempDir Path temp;

    @Test void aesDecryptsOnlyWithCorrectKeyAndRejectsWrongKeySize() throws Exception {
        Path key = temp.resolve("AadTexSecret.key"); Files.writeString(key, "AadTexSecret2026");
        byte[] expected = "confidential csv".getBytes(StandardCharsets.UTF_8);
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec("AadTexSecret2026".getBytes(StandardCharsets.UTF_8), "AES"));
        CryptoService service = new CryptoService();
        assertArrayEquals(expected, service.decrypt(cipher.doFinal(expected), key));
        Files.writeString(key, "BadKey1234567890");
        assertThrows(IllegalArgumentException.class, () -> service.decrypt(cipher.doFinal(expected), key));
        Files.writeString(key, "wrong");
        assertThrows(IllegalArgumentException.class, () -> service.decrypt(new byte[]{1,2,3}, key));
    }

    @Test void readsSpanishAndChineseUtf8AndCalculatesExactMoneyTotals() throws Exception {
        CsvSalesService service = new CsvSalesService();
        var spain = service.read(new ByteArrayInputStream((CsvSalesService.HEADER + "\n" +
                "A,Zara Gran Vía Madrid,14500.50,WOMEN,2026-09-13\n" +
                "B,Zara Puerta del Sol,3600.30,MEN,2026-09-13\n").getBytes(StandardCharsets.UTF_8)), "Spain");
        assertEquals("Zara Gran Vía Madrid", spain.get(0).storeName());
        assertEquals("18100.80", spain.stream().map(s -> s.amount()).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add).toPlainString());
        var china = service.read(new ByteArrayInputStream((CsvSalesService.HEADER + "\n"+
                "C,Zara 上海南京东路店,28900.00,WOMEN,2026-09-13\n").getBytes(StandardCharsets.UTF_8)), "China");
        assertEquals("Zara 上海南京东路店", china.get(0).storeName());
    }

    @Test void skipsBlankAndMalformedRowsButRejectsBadHeader() throws Exception {
        CsvSalesService service = new CsvSalesService();
        var result = service.read(new ByteArrayInputStream((CsvSalesService.HEADER + "\n\nA,Name,not-a-number,MEN,2026-09-13\nB,Valid,1.20,MEN,2026-09-13\n").getBytes(StandardCharsets.UTF_8)), "mixed");
        assertEquals(1, result.size());
        assertThrows(java.io.IOException.class, () -> service.read(new ByteArrayInputStream("bad\n".getBytes(StandardCharsets.UTF_8)), "bad"));
    }

    @Test void generatorAndProcessorHandleAllBatchesZipJpegsBrokenAndJson() throws Exception {
        StoreDataProperties paths = new StoreDataProperties(temp.toString());
        new MultiLanguageDataGenerator(paths).run();
        assertEquals(0, Files.size(paths.input().resolve("daily_sales_broken_store.csv")));
        byte[] original = Files.readAllBytes(paths.input().resolve("signature_china.jpg"));
        assertArrayEquals(new byte[]{(byte)255,(byte)216,(byte)255}, java.util.Arrays.copyOf(original, 3));
        new StoreBatchProcessor(paths, new CsvSalesService(), new CryptoService(), new CloudBucketService(paths)).processStoreBatches();
        assertFalse(Files.exists(paths.input().resolve("batch_usa.zip")));
        assertTrue(Files.exists(paths.processed().resolve("batch_usa.zip")));
        assertTrue(Files.exists(paths.errors().resolve("daily_sales_broken_store.csv")));
        for (String name : new String[]{"signature_spain.jpg","signature_china.jpg","signature_financial.jpg","signature_usa.jpg"})
            {
                Path copied = paths.signatures().resolve(name);
                assertArrayEquals(original, Files.readAllBytes(copied));
                assertNotNull(javax.imageio.ImageIO.read(copied.toFile()), "Debe ser un JPEG que pueda leer ImageIO: " + name);
            }
        try (var summaries = Files.list(paths.bucket())) {
            var files = summaries.filter(p -> p.toString().endsWith(".json")).toList();
            assertEquals(4, files.size());
            var json = files.stream().map(p -> {
                try { return Files.readString(p); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }).toList();
            assertTrue(json.stream().anyMatch(s -> s.contains("daily_sales_spain.csv") && s.contains("20100.80")));
            assertTrue(json.stream().anyMatch(s -> s.contains("daily_sales_china.csv") && s.contains("41300.80")));
            assertTrue(json.stream().anyMatch(s -> s.contains("daily_sales_financial.enc") && s.contains("95000.00")));
            assertTrue(json.stream().anyMatch(s -> s.contains("batch_usa.zip") && s.contains("68450.75")));
        }
    }

    @Test void emptyInputIsCreatedAndSafeToProcess() throws Exception {
        StoreDataProperties paths = new StoreDataProperties(temp.resolve("not-created").toString());
        new StoreBatchProcessor(paths, new CsvSalesService(), new CryptoService(), new CloudBucketService(paths)).processStoreBatches();
        assertTrue(Files.isDirectory(paths.input()));
        assertTrue(Files.isDirectory(paths.errors()));
    }

    @Test void schedulerDeclaresRequiredFixedRate() throws Exception {
        Scheduled scheduled = StoreIngestionScheduler.class.getMethod("processStoreBatches").getAnnotation(Scheduled.class);
        assertNotNull(scheduled);
        assertEquals(5000L, scheduled.fixedRate());
        Scheduled cron = CronStoreIngestionScheduler.class.getMethod("processStoreBatches").getAnnotation(Scheduled.class);
        assertNotNull(cron);
        assertEquals("${aadtex.scheduler.cron:*/5 * * * * *}", cron.cron());
    }
}
