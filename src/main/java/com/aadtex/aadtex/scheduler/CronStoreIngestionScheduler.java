package com.aadtex.aadtex.scheduler;

import com.aadtex.aadtex.service.StoreBatchProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aadtex.scheduler.mode", havingValue = "cron", matchIfMissing = true)
public class CronStoreIngestionScheduler {
    private static final Logger log = LoggerFactory.getLogger(CronStoreIngestionScheduler.class);
    private final StoreBatchProcessor processor;
    public CronStoreIngestionScheduler(StoreBatchProcessor processor) { this.processor = processor; }

    @Scheduled(cron = "${aadtex.scheduler.cron:*/5 * * * * *}")
    public void processStoreBatches() {
        log.debug("Inicio de ciclo programado cron.");
        processor.processStoreBatches();
    }
}
