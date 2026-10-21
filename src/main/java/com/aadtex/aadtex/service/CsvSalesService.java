package com.aadtex.aadtex.service;

import com.aadtex.aadtex.model.Sale;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CsvSalesService {
    private static final Logger log = LoggerFactory.getLogger(CsvSalesService.class);
    public static final String HEADER = "StoreID,StoreName,SalesAmount,Category,TransactionDate";

    public List<Sale> read(InputStream input, String source) throws IOException {
        List<Sale> sales = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null || !HEADER.equals(header.stripLeading().replace("\uFEFF", "")))
                throw new IOException("Encabezado CSV no válido en " + source);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) continue;
                String[] fields = line.split(",", -1);
                try {
                    if (fields.length != 5) throw new IllegalArgumentException("Se esperaban cinco columnas");
                    sales.add(new Sale(fields[0].trim(), fields[1].trim(), new BigDecimal(fields[2].trim()),
                            fields[3].trim(), LocalDate.parse(fields[4].trim())));
                } catch (RuntimeException ex) {
                    String reason = ex instanceof NumberFormatException ? "importe inválido"
                            : ex instanceof java.time.format.DateTimeParseException ? "fecha inválida" : "campos inválidos";
                    log.warn("Línea CSV omitida en {}:{} ({})", source, lineNumber, reason);
                }
            }
        }
        if (sales.isEmpty()) throw new IOException("El CSV no contiene ventas válidas: " + source);
        return sales;
    }
}
