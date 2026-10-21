package com.aadtex.aadtex.scheduler;

import com.aadtex.aadtex.service.StoreBatchProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aadtex.scheduler.mode", havingValue = "fixed-rate")
public class StoreIngestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(StoreIngestionScheduler.class);
    private final StoreBatchProcessor processor;
    public StoreIngestionScheduler(StoreBatchProcessor processor) { this.processor = processor; }

    @Scheduled(fixedRate = 5000)
    public void processStoreBatches() {
        log.debug("Inicio de ciclo programado fixedRate=5000 ms.");
        processor.processStoreBatches();
    }
}
